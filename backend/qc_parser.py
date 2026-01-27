from __future__ import annotations

from typing import Dict, Any, List, Tuple
import logging
import time

import fitz  # PyMuPDF
import numpy as np
from PIL import Image

from ocr.ocr_adapter import ocr_image_to_text
from ocr.header_extract import extract_page1_header

logger = logging.getLogger(__name__)

# -----------------------------
# Discovery
# -----------------------------
# Tighten keywords to reduce false positives.
WRITEUP_KEYWORDS = (
    "test comments and/or rework required",
    "rework required",
    "test comments",
)

DISCOVERY_DPI = 110
DISCOVERY_BAND_RATIO = 0.20  # top band for keyword OCR
MARK_COUNT_DPI = 170         # moderate DPI for reliable row segmentation

# If you really want an early-exit, use "stop after N consecutive non-matches AFTER first match".
STOP_AFTER_CONSECUTIVE_MISSES = 6


def _render_page_from_doc(doc: fitz.Document, page_index: int, dpi: int) -> Image.Image:
    page = doc.load_page(page_index)
    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)
    pix = page.get_pixmap(matrix=mat, alpha=False)
    return Image.frombytes("RGB", (pix.width, pix.height), pix.samples)


def looks_like_writeup_page(page: Image.Image, band_ratio: float = DISCOVERY_BAND_RATIO) -> bool:
    w, h = page.size
    band = page.crop((0, 0, w, int(h * band_ratio)))
    text = ocr_image_to_text(band).lower()
    return any(k in text for k in WRITEUP_KEYWORDS)


# -----------------------------
# Header extraction (page 1)
# -----------------------------
def extract_header_info(page0: Image.Image) -> Dict[str, str | None]:
    d = extract_page1_header(page0, ocr_func=ocr_image_to_text)
    return {
        "project_number": d.get("project_number"),
        "customer_name": d.get("project_name"),
        "project_manager": d.get("project_manager"),
        "date": d.get("date"),
        "raw_text": d.get("raw_text"),
    }


# -----------------------------
# ENG / MFG issue counting (ROW-BASED)
# -----------------------------
def _to_gray_np(img: Image.Image) -> np.ndarray:
    return np.array(img.convert("L"))


def _roi(img: Image.Image, x0: float, y0: float, x1: float, y1: float) -> Image.Image:
    w, h = img.size
    return img.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1)))


def _find_horizontal_lines_y(ink: np.ndarray) -> List[int]:
    """
    ink: 2D uint8 array with 1=ink, 0=paper
    Returns y positions (centers) of strong horizontal lines.
    """
    row_density = ink.mean(axis=1)
    # Horizontal grid lines are among the densest rows.
    line_cut = float(np.percentile(row_density, 94))  # tune 92-96 if needed
    is_line = row_density >= line_cut

    ys: List[int] = []
    in_run = False
    start = 0
    for y, v in enumerate(is_line):
        if v and not in_run:
            in_run = True
            start = y
        elif not v and in_run:
            in_run = False
            end = y - 1
            ys.append((start + end) // 2)
    if in_run:
        ys.append((start + len(is_line) - 1) // 2)

    # de-duplicate near neighbors
    dedup: List[int] = []
    for y in ys:
        if not dedup or abs(y - dedup[-1]) > 6:
            dedup.append(y)
    return dedup


def _row_bands_from_lines(line_ys: List[int], min_height: int) -> List[Tuple[int, int]]:
    bands: List[Tuple[int, int]] = []
    for a, b in zip(line_ys, line_ys[1:]):
        y0 = a + 2
        y1 = b - 2
        if (y1 - y0) >= min_height:
            bands.append((y0, y1))
    return bands


def count_eng_mfg(page: Image.Image) -> Dict[str, int]:
    """
    Counts issues as ROWS in the write-up table by:
    - cropping the write-up table
    - finding horizontal separators
    - for each row band, checking ink density inside ENG/MFG cells
    """

    # 1) Crop the full table region.
    # NOTE: tune once for your template.
    table = _roi(page, x0=0.03, y0=0.10, x1=0.97, y1=0.94)

    g = _to_gray_np(table)

    # 2) Binarize ink/paper with percentile threshold
    thr = np.percentile(g, 35)
    ink = (g < thr).astype(np.uint8)

    # 3) Find horizontal grid lines -> row segmentation
    line_ys = _find_horizontal_lines_y(ink)
    if len(line_ys) < 8:
        # not enough structure to safely count
        return {"engineering": 0, "manufacturing": 0}

    # 4) Build row bands
    min_row_h = 18
    bands = _row_bands_from_lines(line_ys, min_height=min_row_h)

    # 5) Define ENG/MFG cell x-ranges within TABLE
    # These ratios are based on your layout: Dept columns near the right.
    tw = ink.shape[1]
    eng_x0, eng_x1 = int(tw * 0.78), int(tw * 0.84)
    mfg_x0, mfg_x1 = int(tw * 0.84), int(tw * 0.90)

    def mark_present(y0: int, y1: int, x0: int, x1: int) -> bool:
        cell = ink[y0:y1, x0:x1]
        # ignore borders: only center portion of cell width
        w = cell.shape[1]
        cx0 = int(w * 0.20)
        cx1 = int(w * 0.80)
        center = cell[:, cx0:cx1]
        dens = float(center.mean())
        # threshold tuned to avoid counting gridlines as marks
        return dens > 0.07  # tune 0.06–0.10 if needed

    eng = 0
    mfg = 0

    # Optional: skip the header band if the first few bands are the column header area
    # Heuristic: ignore the first 1-2 bands (often table header)
    start_idx = 2 if len(bands) > 6 else 0

    for (y0, y1) in bands[start_idx:]:
        has_eng = mark_present(y0, y1, eng_x0, eng_x1)
        has_mfg = mark_present(y0, y1, mfg_x0, mfg_x1)

        # count row once
        if has_eng and not has_mfg:
            eng += 1
        elif has_mfg and not has_eng:
            mfg += 1
        elif has_eng and has_mfg:
            # rare: choose deterministic behavior
            eng += 1

    return {"engineering": int(eng), "manufacturing": int(mfg)}


# -----------------------------
# Main parser
# -----------------------------
def parse_qc_pdf(pdf_bytes: bytes) -> Dict[str, Any]:
    t0 = time.perf_counter()
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")

    discovery_s = 0.0
    count_s = 0.0
    candidates: List[int] = []
    header: Dict[str, Any] = {}
    totals = {"engineering": 0, "manufacturing": 0}

    try:
        if doc.page_count == 0:
            return {"writeups": totals, "writeup_pages": []}

        # Header extraction at higher DPI for accuracy
        header_img = _render_page_from_doc(doc, page_index=0, dpi=220)
        header = extract_header_info(header_img)

        # Discovery pass: low DPI, OCR only top band
        discovery_start = time.perf_counter()
        misses_after_first = 0
        seen_first = False

        for idx in range(doc.page_count):
            page_img = _render_page_from_doc(doc, page_index=idx, dpi=DISCOVERY_DPI)
            if looks_like_writeup_page(page_img, band_ratio=DISCOVERY_BAND_RATIO):
                candidates.append(idx)
                seen_first = True
                misses_after_first = 0
            else:
                if seen_first:
                    misses_after_first += 1
                    if misses_after_first >= STOP_AFTER_CONSECUTIVE_MISSES:
                        break

        discovery_s = time.perf_counter() - discovery_start

        # Count marks on candidate pages, at moderate DPI
        count_start = time.perf_counter()
        for idx in candidates:
            page = _render_page_from_doc(doc, page_index=idx, dpi=MARK_COUNT_DPI)
            counts = count_eng_mfg(page)
            totals["engineering"] += counts["engineering"]
            totals["manufacturing"] += counts["manufacturing"]
        count_s = time.perf_counter() - count_start

    finally:
        doc.close()

    total_s = time.perf_counter() - t0
    logger.info(
        "qc_parse discovery_s=%.3f count_s=%.3f total_s=%.3f candidates=%s totals=%s",
        discovery_s,
        count_s,
        total_s,
        candidates,
        totals,
    )

    return {
        **header,
        "writeups": totals,
        "writeup_pages": candidates,
        "candidate_pages": candidates,
    }
