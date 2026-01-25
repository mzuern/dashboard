from __future__ import annotations

from typing import Dict, Any, List, Tuple
import re

import fitz  # PyMuPDF
import numpy as np
from PIL import Image

from ocr.ocr_adapter import ocr_image_to_text
from ocr.header_extract import extract_page1_header
from ocr.pdf_render import render_pdf_bytes_page_to_pil


# -----------------------------
# PDF -> images
# -----------------------------
def pdf_to_pil_images(pdf_bytes: bytes, dpi: int = 220) -> List[Image.Image]:
    """
    Render each PDF page to PIL Image.
    Uses your existing renderer so we don't fork logic.
    """
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    pages: List[Image.Image] = []
    for i in range(len(doc)):
        pages.append(render_pdf_bytes_page_to_pil(pdf_bytes, page_index=i, dpi=dpi))
    return pages


# -----------------------------
# Header extraction (page 1)
# -----------------------------
def extract_header_info(page0: Image.Image) -> Dict[str, str | None]:
    """
    Use the header_extract pipeline you already have (keywords + stop rules).
    """
    d = extract_page1_header(page0, ocr_func=ocr_image_to_text)
    return {
        "project_number": d.get("project_number"),
        "customer_name": d.get("project_name"),
        "project_manager": d.get("project_manager"),
        "date": d.get("date"),
        "raw_text": d.get("raw_text"),
    }


# -----------------------------
# Page discovery: find "writeup" pages
# -----------------------------
WRITEUP_KEYWORDS = (
    "rework",
    "write up",
    "write-up",
    "test comments",
    "comments",
)

def looks_like_writeup_page(page: Image.Image) -> bool:
    """
    OCR only the top band (fast) and keyword match.
    """
    w, h = page.size
    band = page.crop((0, 0, w, int(h * 0.18)))
    text = ocr_image_to_text(band).lower()
    return any(k in text for k in WRITEUP_KEYWORDS)


# -----------------------------
# ENG / MFG mark counting (no Tesseract)
# -----------------------------
def _to_gray_np(img: Image.Image) -> np.ndarray:
    return np.array(img.convert("L"))


def _roi(img: Image.Image, x0: float, y0: float, x1: float, y1: float) -> Image.Image:
    """
    ROI coords are expressed as ratios [0..1] of page width/height.
    """
    w, h = img.size
    return img.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1)))


def _count_marks_in_column(col_img: Image.Image) -> int:
    """
    Very simple mark detector:
    - convert to grayscale
    - binarize (adaptive-ish threshold via percentile)
    - count connected-ish blobs by scanning rows

    This is intentionally lightweight and avoids OpenCV.
    It works well when boxes are consistent and marks are dark.
    """
    g = _to_gray_np(col_img)

    # threshold: treat dark pixels as ink
    thresh = np.percentile(g, 35)  # tune if needed (lower = stricter)
    ink = (g < thresh).astype(np.uint8)

    # collapse each row to "ink density"
    row_density = ink.mean(axis=1)

    # A "mark row" tends to have a spike vs empty box
    # Use a dynamic threshold so it adapts to scan darkness
    cut = max(0.04, float(np.percentile(row_density, 85)) * 0.65)
    hits = row_density > cut

    # Count contiguous hit-runs (each run ≈ one checked box row)
    count = 0
    in_run = False
    min_run = max(3, int(len(hits) * 0.004))  # ignore tiny noise runs
    run_len = 0

    for v in hits:
        if v:
            if not in_run:
                in_run = True
                run_len = 1
            else:
                run_len += 1
        else:
            if in_run:
                if run_len >= min_run:
                    count += 1
                in_run = False
                run_len = 0

    if in_run and run_len >= min_run:
        count += 1

    return count


def count_eng_mfg(page: Image.Image) -> Dict[str, int]:
    """
    Count marks under ENG and MFG columns.
    Because your layout is consistent, we use fixed ROIs (ratios).
    You tune these once and you're done.

    Based on your screenshot, ENG/MFG are a narrow pair of columns near the right side.
    Adjust x ratios if needed.
    """
    # 1) Crop the table region where the boxes live (skip header and footer)
    # Tune y0/y1 as needed depending on where the checklist starts.
    table = _roi(page, x0=0.05, y0=0.20, x1=0.95, y1=0.92)

    # 2) Now take two narrow vertical slices where ENG and MFG boxes are.
    # These numbers are starting guesses. We'll tune quickly using 1–2 PDFs.
    eng_col = _roi(table, x0=0.72, y0=0.00, x1=0.80, y1=1.00)
    mfg_col = _roi(table, x0=0.80, y0=0.00, x1=0.88, y1=1.00)

    eng = _count_marks_in_column(eng_col)
    mfg = _count_marks_in_column(mfg_col)

    return {"engineering": int(eng), "manufacturing": int(mfg)}


# -----------------------------
# Main parser
# -----------------------------
def parse_qc_pdf(pdf_bytes: bytes) -> Dict[str, Any]:
    pages = pdf_to_pil_images(pdf_bytes, dpi=220)
    header = extract_header_info(pages[0])

    totals = {"engineering": 0, "manufacturing": 0}
    writeup_pages: List[int] = []

    for idx, page in enumerate(pages):
        if looks_like_writeup_page(page):
            writeup_pages.append(idx)
            counts = count_eng_mfg(page)
            totals["engineering"] += counts["engineering"]
            totals["manufacturing"] += counts["manufacturing"]

    return {
        **header,
        "writeups": totals,
        "writeup_pages": writeup_pages,
    }
