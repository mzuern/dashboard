from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Dict, List, Tuple
from datetime import date, datetime

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


DATE_RE = re.compile(r"\b(\d{1,2})[\-/](\d{1,2})(?:[\-/](\d{2,4}))?\b")


def _parse_date_token(token: str) -> date | None:
    """Parse mm/dd(/yy|yyyy) or mm-dd(/yy|yyyy). If year missing, assume current year."""
    m = DATE_RE.search(token)
    if not m:
        return None
    mm = int(m.group(1))
    dd = int(m.group(2))
    yy = m.group(3)
    if yy is None:
        yyyy = date.today().year
    else:
        y = int(yy)
        yyyy = 2000 + y if y < 100 else y
    try:
        return date(yyyy, mm, dd)
    except Exception:
        return None


def _preprocess_for_ocr(img_rgb: np.ndarray) -> np.ndarray:
    """Cheap binarize to improve OCR consistency."""
    # import cv2 lazily: opencv isn't always present in minimal installs
    try:
        import cv2  # type: ignore

        gray = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2GRAY)
        gray = cv2.bilateralFilter(gray, 7, 60, 60)
        thr = cv2.adaptiveThreshold(
            gray,
            255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY,
            31,
            10,
        )
        return thr
    except Exception:
        return img_rgb


def estimate_open_closed_oldest(writeup_pages_rgb: List[np.ndarray]) -> Dict[str, Any]:
    """Best-effort open/closed + oldest open age.

    We infer per-row status using OCR token positions:
      - 'opened' date comes from left-most date column
      - 'closed' if we detect any date token in the right-side corrected/retested area

    This is NOT perfect, but it's good enough to drive a first metrics dashboard.
    """

    open_count = 0
    closed_count = 0
    oldest_open: date | None = None

    for page_rgb in writeup_pages_rgb:
        h, w = page_rgb.shape[0], page_rgb.shape[1]

        img = _preprocess_for_ocr(page_rgb)
        data = pytesseract.image_to_data(img, output_type=pytesseract.Output.DICT)
        n = len(data.get("text", []))

        # Collect date tokens with their x/y centers
        tokens: List[Tuple[date, float, float]] = []
        for i in range(n):
            t = (data["text"][i] or "").strip()
            if not t:
                continue
            d = _parse_date_token(t)
            if not d:
                continue
            x = float(data.get("left", [0])[i]) + float(data.get("width", [0])[i]) / 2.0
            y = float(data.get("top", [0])[i]) + float(data.get("height", [0])[i]) / 2.0
            tokens.append((d, x, y))

        if not tokens:
            continue

        # Cluster tokens into rows by y (simple bucket)
        row_buckets: Dict[int, List[Tuple[date, float, float]]] = {}
        row_height = max(int(h * 0.025), 18)  # heuristic
        for d, x, y in tokens:
            bucket = int(y // row_height)
            row_buckets.setdefault(bucket, []).append((d, x, y))

        # For each row: opened = leftmost date; closed if any date appears on far-right
        right_x_threshold = w * 0.72
        left_x_threshold = w * 0.35

        for _bucket, row in row_buckets.items():
            # Must have at least one "left" date, otherwise skip (avoids header/footer)
            left_dates = [d for (d, x, _y) in row if x <= left_x_threshold]
            if not left_dates:
                continue
            opened = min(left_dates)

            has_right_date = any(x >= right_x_threshold for (_d, x, _y) in row)
            if has_right_date:
                closed_count += 1
            else:
                open_count += 1
                if oldest_open is None or opened < oldest_open:
                    oldest_open = opened

    oldest_open_days: int | None = None
    if oldest_open is not None:
        oldest_open_days = max((date.today() - oldest_open).days, 0)

    return {
        "open_issue_count": int(open_count),
        "closed_issue_count": int(closed_count),
        "oldest_open_days": oldest_open_days,
    }


# -------------------------
# Extra (best-effort) metrics
# -------------------------
# We only need a rough estimate of "open" vs "closed" and aging for a demo.
# Strategy: OCR the writeup pages and cluster tokens by their Y coordinate into rows.
# For each row, treat the first date-like token as the "opened" date.
# If any additional date-like token appears near the far-right corrected/retested area,
# we treat the row as "closed"; otherwise it's open.

DATE_RE = re.compile(r"\b(\d{1,2})[\-/](\d{1,2})(?:[\-/](\d{2,4}))?\b")


def _parse_date_token(s: str) -> date | None:
    s = (s or "").strip()
    m = DATE_RE.search(s)
    if not m:
        return None
    mm = int(m.group(1))
    dd = int(m.group(2))
    yy = m.group(3)
    # If year missing, assume current year (good enough for dashboard demo)
    if yy is None:
        yyyy = date.today().year
    else:
        y = int(yy)
        yyyy = 2000 + y if y < 100 else y
    try:
        return date(yyyy, mm, dd)
    except Exception:
        return None


def estimate_open_closed_and_oldest(page_rgb: "np.ndarray") -> Dict[str, Any]:
    """Best-effort extraction of open/closed counts + oldest open age from a writeup page."""
    try:
        import pytesseract
        from pytesseract import Output
    except Exception:
        return {"open": 0, "closed": 0, "oldest_open_days": None}

    h, w = page_rgb.shape[:2]

    # OCR tokens with positions
    ocr = pytesseract.image_to_data(page_rgb, output_type=Output.DICT)
    n = len(ocr.get("text", []))

    tokens: List[Tuple[int, int, int, str]] = []  # (y, x, line_num, text)
    for i in range(n):
        t = (ocr["text"][i] or "").strip()
        if not t:
            continue
        x = int(ocr.get("left", [0])[i])
        y = int(ocr.get("top", [0])[i])
        ln = int(ocr.get("line_num", [0])[i])
        tokens.append((y, x, ln, t))

    if not tokens:
        return {"open": 0, "closed": 0, "oldest_open_days": None}

    # Cluster into "rows" by (block/line_num) isn't stable across scans.
    # Use y-binning with a small threshold.
    tokens.sort(key=lambda t: (t[0], t[1]))
    rows: List[List[Tuple[int, int, int, str]]] = []
    Y_TOL = max(6, int(h * 0.006))  # adaptive tolerance

    for tok in tokens:
        if not rows:
            rows.append([tok])
            continue
        if abs(tok[0] - rows[-1][0][0]) <= Y_TOL:
            rows[-1].append(tok)
        else:
            rows.append([tok])

    opened_dates: List[date] = []
    open_count = 0
    closed_count = 0

    # Right-side corrected/retested columns live roughly on the right 35% of the page.
    right_x_threshold = int(w * 0.65)

    for row in rows:
        # Filter out header-ish rows by requiring some handwriting-ish content.
        texts = [t[3] for t in row]
        dates = [d for t in texts if (d := _parse_date_token(t))]
        if not dates:
            continue
        opened = dates[0]

        # Any date token far-right means corrected/retested date exists.
        right_dates = []
        for (y, x, ln, t) in row:
            d = _parse_date_token(t)
            if d and x >= right_x_threshold:
                right_dates.append(d)

        if right_dates:
            closed_count += 1
        else:
            open_count += 1
            opened_dates.append(opened)

    oldest_open_days = None
    if opened_dates:
        oldest = min(opened_dates)
        oldest_open_days = (date.today() - oldest).days

    return {"open": open_count, "closed": closed_count, "oldest_open_days": oldest_open_days}


def parse_qc_pdf(pdf_bytes: bytes) -> Dict[str, Any]:
    pages = pdf_to_images(pdf_bytes)
    meta = extract_header_meta(pages[0]) if pages else QcMeta(None, None, None)

    totals = {"eng": 0, "mfg": 0}
    open_total = 0
    closed_total = 0
    oldest_open_days: int | None = None
    for p in pages:
        if page_looks_like_writeup(p):
            c = count_dept_writeups(p)
            totals["eng"] += c["eng"]
            totals["mfg"] += c["mfg"]

            # Best-effort aging metrics
            oc = estimate_open_closed_and_oldest(p)
            open_total += int(oc.get("open", 0))
            closed_total += int(oc.get("closed", 0))
            od = oc.get("oldest_open_days")
            if isinstance(od, int):
                oldest_open_days = od if oldest_open_days is None else max(oldest_open_days, od)

    return {
        "project_number": meta.project_number,
        "customer_name": meta.customer_name,
        "project_manager": meta.project_manager,
        "writeups_by_dept": totals,
        "open_issue_count": open_total,
        "closed_issue_count": closed_total,
        "oldest_open_days": oldest_open_days,
    }
