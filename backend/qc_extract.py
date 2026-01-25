from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Dict, List, Tuple

import fitz  # pymupdf
from PIL import Image
import numpy as np
import pytesseract


@dataclass
class QcMeta:
    project_number: str | None
    customer_name: str | None
    project_manager: str | None


def pdf_to_images(pdf_bytes: bytes, zoom: float = 2.0) -> List[np.ndarray]:
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    images: List[np.ndarray] = []
    mat = fitz.Matrix(zoom, zoom)
    for page in doc:
        pix = page.get_pixmap(matrix=mat, alpha=False)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        images.append(np.array(img))  # RGB uint8
    return images


def ocr_text(img_rgb: np.ndarray) -> str:
    return pytesseract.image_to_string(img_rgb)


def ocr_data(img_rgb: np.ndarray) -> Dict[str, List[Any]]:
    return pytesseract.image_to_data(img_rgb, output_type=pytesseract.Output.DICT)


def extract_header_meta(page1_rgb: np.ndarray) -> QcMeta:
    # Crop top ~20% (tweak if needed)
    h, w, _ = page1_rgb.shape
    crop = page1_rgb[0:int(h * 0.22), 0:w]

    text = ocr_text(crop)

    # Robust-ish regex patterns (tweak once after seeing outputs)
    proj = None
    pm = None
    cust = None

    # Project number
    m = re.search(r"(Project|Job)\s*(No|#)\s*[:\-]?\s*([A-Za-z0-9\-]+)", text, re.IGNORECASE)
    if m:
        proj = m.group(3).strip()

    # Project manager
    m = re.search(r"(Project\s*Manager|PM)\s*[:\-]?\s*(.+)", text, re.IGNORECASE)
    if m:
        pm = m.group(2).strip()
        pm = re.split(r"\s{2,}", pm)[0].strip()

    # Customer / Project Name
    m = re.search(r"(Customer|Project\s*Name)\s*[:\-]?\s*(.+)", text, re.IGNORECASE)
    if m:
        cust = m.group(2).strip()
        cust = re.split(r"\s{2,}", cust)[0].strip()

    return QcMeta(project_number=proj, customer_name=cust, project_manager=pm)


def page_looks_like_writeup(page_rgb: np.ndarray) -> bool:
    # Quick cheap check: OCR a small strip where the title usually lives
    h, w, _ = page_rgb.shape
    strip = page_rgb[int(h * 0.00):int(h * 0.18), 0:w]
    t = ocr_text(strip).lower()
    return ("rework required" in t) or ("write up" in t) or ("test comments" in t)


def count_dept_writeups(page_rgb: np.ndarray) -> Dict[str, int]:
    d = ocr_data(page_rgb)

    words = [ (d["text"][i] or "").strip() for i in range(len(d["text"])) ]
    left  = d["left"]; top = d["top"]; width = d["width"]; height = d["height"]

    # Find column header x-centers for Eng and Mfg
    eng_x = None
    mfg_x = None
    header_y = None

    for i, wtxt in enumerate(words):
        t = wtxt.lower()
        if t == "eng":
            eng_x = left[i] + width[i] / 2
            header_y = top[i]  # approximate header row
        elif t == "mfg":
            mfg_x = left[i] + width[i] / 2
            header_y = top[i] if header_y is None else min(header_y, top[i])

    if eng_x is None or mfg_x is None or header_y is None:
        return {"eng": 0, "mfg": 0}

    # Collect X marks below header
    x_marks: List[Tuple[float, float]] = []
    for i, wtxt in enumerate(words):
        if wtxt.upper() == "X":
            y_mid = top[i] + height[i] / 2
            if y_mid > header_y + 30:  # below header row
                x_mid = left[i] + width[i] / 2
                x_marks.append((x_mid, y_mid))

    # Group by row using y clustering
    x_marks.sort(key=lambda p: p[1])
    rows: List[List[Tuple[float,float]]] = []
    ROW_TOL = 18  # tweak if needed

    for xm, ym in x_marks:
        if not rows:
            rows.append([(xm, ym)])
            continue
        if abs(ym - rows[-1][0][1]) <= ROW_TOL:
            rows[-1].append((xm, ym))
        else:
            rows.append([(xm, ym)])

    eng_rows = 0
    mfg_rows = 0

    for row in rows:
        # If multiple Xs, pick the one closest to Eng/Mfg columns
        # Assign row dept based on nearest column.
        best = None
        for xm, ym in row:
            de = abs(xm - eng_x)
            dm = abs(xm - mfg_x)
            dept = "eng" if de <= dm else "mfg"
            dist = min(de, dm)
            if best is None or dist < best[0]:
                best = (dist, dept)
        if best:
            if best[1] == "eng":
                eng_rows += 1
            else:
                mfg_rows += 1

    return {"eng": eng_rows, "mfg": mfg_rows}


def parse_qc_pdf(pdf_bytes: bytes) -> Dict[str, Any]:
    pages = pdf_to_images(pdf_bytes)
    meta = extract_header_meta(pages[0]) if pages else QcMeta(None, None, None)

    totals = {"eng": 0, "mfg": 0}
    for p in pages:
        if page_looks_like_writeup(p):
            c = count_dept_writeups(p)
            totals["eng"] += c["eng"]
            totals["mfg"] += c["mfg"]

    return {
        "project_number": meta.project_number,
        "customer_name": meta.customer_name,
        "project_manager": meta.project_manager,
        "writeups_by_dept": totals,
    }
