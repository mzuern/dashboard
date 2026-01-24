# C:\OCR\extract_writeup_metrics.py
from __future__ import annotations


import re
import csv
from dataclasses import dataclass
from datetime import datetime, date
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


import fitz  # pymupdf
import cv2
import numpy as np
from paddleocr import PaddleOCR




# -----------------------
# CONFIG (you will tune)
# -----------------------


# These page numbers are 0-based indexes in PyMuPDF.
# For 4009_001_1.pdf (18 pages), Page 1 visible in viewer == index 0.
HEADER_PAGE_INDEX = 0


# Writeup pages: in your 4009_001_1.pdf, "Test Comments and/or Rework Required"
# appear around the middle (pages that look like lined rows).
# Start with these, adjust after first run:
WRITEUP_PAGE_INDEXES = list(range(6, 16))  # 6..15 inclusive (7th..16th pages)


# DPI for rendering PDF pages to images
RENDER_DPI = 200


# --- Template crop boxes (x1, y1, x2, y2) in *pixels* AFTER rendering ---
# We will compute these dynamically after we render the first page and inspect its size.
# For now we define them as RELATIVE FRACTIONS of the image size (works across DPI).
# You will tune these once.


# Page 1 header strip area (contains Project No, Project Name, Date, Project Manager)
HEADER_CROP = (0.00, 0.00, 1.00, 0.20)  # top 20%


# On writeup pages, the "Dept" checkbox area is on the right side.
# We'll crop the whole writeup table region then separately look for Eng/Mfg tokens + X marks.
WRITEUP_TABLE_CROP = (0.00, 0.12, 1.00, 0.92)  # most of the page excluding top title & bottom footer


# Narrow crop of just the Dept columns (Eng/Test/Mfg) area on the right
DEPT_CROP = (0.70, 0.12, 0.86, 0.92)


# Crop of the left "Write Up / By / Date" columns (to pick up row dates)
LEFT_META_CROP = (0.00, 0.12, 0.20, 0.92)


# Crop of the "Corrected/Retested dates" columns area on the far right
RIGHT_STATUS_CROP = (0.86, 0.12, 1.00, 0.92)




# -----------------------
# DATA MODELS
# -----------------------


@dataclass
class ProjectMetrics:
    source_file: str
    project_no: str
    customer_or_project_name: str
    project_manager: str
    eng_issues: int
    mfg_issues: int
    open_issues: int
    oldest_open_days: int
    avg_open_days: float




# -----------------------
# HELPERS
# -----------------------


def render_page_to_bgr(doc: fitz.Document, page_index: int, dpi: int) -> np.ndarray:
    page = doc.load_page(page_index)
    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)
    pix = page.get_pixmap(matrix=mat, alpha=False)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3)
    # pix is RGB
    return cv2.cvtColor(img, cv2.COLOR_RGB2BGR)


def crop_rel(img: np.ndarray, rel_box: Tuple[float, float, float, float]) -> np.ndarray:
    h, w = img.shape[:2]
    x1 = int(rel_box[0] * w)
    y1 = int(rel_box[1] * h)
    x2 = int(rel_box[2] * w)
    y2 = int(rel_box[3] * h)
    return img[y1:y2, x1:x2].copy()


def ocr_lines(ocr: PaddleOCR, img_bgr: np.ndarray) -> List[str]:
    # PaddleOCR wants RGB or path; we convert to RGB array
    rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    result = ocr.predict(rgb)


    # result format (newer PaddleOCR): list[dict] where dict contains rec_texts
    lines: List[str] = []
    if isinstance(result, list) and len(result) > 0 and isinstance(result[0], dict):
        rec_texts = result[0].get("rec_texts", [])
        for t in rec_texts:
            if isinstance(t, str) and t.strip():
                lines.append(t.strip())
        return lines


    # fallback older formats
    try:
        for page in result:
            for item in page:
                try:
                    _, (text, _conf) = item
                    if text and str(text).strip():
                        lines.append(str(text).strip())
                except Exception:
                    pass
    except Exception:
        pass


    return lines


def find_first(patterns: List[str], text: str) -> str:
    for p in patterns:
        m = re.search(p, text, flags=re.IGNORECASE)
        if m:
            return m.group(1).strip()
    return ""


def parse_header(lines: List[str]) -> Tuple[str, str, str]:
    blob = " | ".join(lines)


    # Project No patterns (tune if your header differs)
    project_no = find_first(
        [
            r"Project\s*No[:#]?\s*([0-9A-Za-z\-]+)",
            r"Proj(?:ect)?\s*No[:#]?\s*([0-9A-Za-z\-]+)",
        ],
        blob,
    )


    project_name = find_first(
        [
            r"Project\s*Name[:#]?\s*([A-Za-z0-9 \-_/]+)",
            r"Customer[:#]?\s*([A-Za-z0-9 \-_/]+)",
        ],
        blob,
    )


    project_manager = find_first(
        [
            r"Project\s*Manager[:#]?\s*([A-Za-z \-']+)",
            r"PM[:#]?\s*([A-Za-z \-']+)",
        ],
        blob,
    )


    return project_no, project_name, project_manager


def parse_date_any(s: str) -> Optional[date]:
    s = s.strip()
    if not s:
        return None
    for fmt in ("%m/%d/%Y", "%m/%d/%y", "%m-%d-%Y", "%m-%d-%y"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            continue
    return None


def extract_row_dates(lines: List[str]) -> List[date]:
    # Pull likely dates from OCR lines in a column crop.
    dates: List[date] = []
    for t in lines:
        # find tokens like 12-2, 12/10, 8/20/2025 etc
        for token in re.findall(r"\b\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?\b", t):
            d = parse_date_any(token if len(token.split("/") if "/" in token else token.split("-")) == 3 else token)
            # if year missing, assume current year (demo-safe)
            if d is None:
                # try adding current year
                parts = re.split(r"[/-]", token)
                if len(parts) == 2:
                    mm, dd = parts
                    try:
                        d = date(date.today().year, int(mm), int(dd))
                    except Exception:
                        d = None
            if d:
                dates.append(d)
    return dates


def classify_dept_counts(dept_lines: List[str]) -> Tuple[int, int]:
    """
    Very simple heuristic:
    - If OCR sees 'Eng' near an 'X' in same line/nearby -> Eng issue
    - If OCR sees 'Mfg' near an 'X' -> Mfg issue


    For a real template, we'd use checkbox image detection per row.
    This is "good enough" for traction/demo.
    """
    eng = 0
    mfg = 0
    joined = "\n".join(dept_lines).lower()


    # crude signals
    # count occurrences of "eng" rows that also have x marks nearby
    eng += len(re.findall(r"eng.*\bx\b|\bx\b.*eng", joined))
    mfg += len(re.findall(r"mfg.*\bx\b|\bx\b.*mfg", joined))


    return eng, mfg




# -----------------------
# MAIN
# -----------------------


def extract_metrics(pdf_path: Path) -> ProjectMetrics:
    ocr = PaddleOCR(lang="en", use_textline_orientation=True)


    doc = fitz.open(str(pdf_path))


    # header
    header_img = render_page_to_bgr(doc, HEADER_PAGE_INDEX, RENDER_DPI)
    header_crop = crop_rel(header_img, HEADER_CROP)
    header_lines = ocr_lines(ocr, header_crop)
    project_no, project_name, project_manager = parse_header(header_lines)


    # writeups
    eng_total = 0
    mfg_total = 0
    open_ages: List[int] = []


    today = date.today()


    for idx in WRITEUP_PAGE_INDEXES:
        if idx < 0 or idx >= doc.page_count:
            continue


        page_img = render_page_to_bgr(doc, idx, RENDER_DPI)


        dept_crop = crop_rel(page_img, DEPT_CROP)
        left_crop = crop_rel(page_img, LEFT_META_CROP)
        right_crop = crop_rel(page_img, RIGHT_STATUS_CROP)


        dept_lines = ocr_lines(ocr, dept_crop)
        left_lines = ocr_lines(ocr, left_crop)
        right_lines = ocr_lines(ocr, right_crop)


        eng_c, mfg_c = classify_dept_counts(dept_lines)
        eng_total += eng_c
        mfg_total += mfg_c


        # open age estimate:
        # - find dates in left (writeup dates)
        # - find dates in right (corrected/retested)
        # If retested date exists => resolved; else open
        write_dates = extract_row_dates(left_lines)
        status_dates = extract_row_dates(right_lines)


        # heuristic: if no status dates, assume items are still open and age based on earliest write date
        if write_dates:
            base = min(write_dates)
            resolved = len(status_dates) > 0
            if not resolved:
                open_ages.append((today - base).days)


    open_issues = len(open_ages)
    oldest_open = max(open_ages) if open_ages else 0
    avg_open = (sum(open_ages) / len(open_ages)) if open_ages else 0.0


    return ProjectMetrics(
        source_file=pdf_path.name,
        project_no=project_no or "(unknown)",
        customer_or_project_name=project_name or "(unknown)",
        project_manager=project_manager or "(unknown)",
        eng_issues=eng_total,
        mfg_issues=mfg_total,
        open_issues=open_issues,
        oldest_open_days=oldest_open,
        avg_open_days=round(avg_open, 2),
    )


def main():
    import argparse


    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", required=True, help="Path to the checklist PDF")
    ap.add_argument("--out", default="metrics.csv", help="Output CSV")
    args = ap.parse_args()


    pdf_path = Path(args.pdf).resolve()
    metrics = extract_metrics(pdf_path)


    out_path = Path(args.out).resolve()
    with out_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(
            f,
            fieldnames=[
                "source_file",
                "project_no",
                "customer_or_project_name",
                "project_manager",
                "eng_issues",
                "mfg_issues",
                "open_issues",
                "oldest_open_days",
                "avg_open_days",
            ],
        )
        w.writeheader()
        w.writerow(metrics.__dict__)


    print("WROTE:", out_path)
    print(metrics)


if __name__ == "__main__":
    main()

