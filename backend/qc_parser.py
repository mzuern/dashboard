from typing import Dict, Any, List
import re
import fitz  # PyMuPDF
import numpy as np
from PIL import Image
import pytesseract




def pdf_to_images(pdf_bytes: bytes) -> List[np.ndarray]:
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    images = []


    for page in doc:
        pix = page.get_pixmap(matrix=fitz.Matrix(2, 2))
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
        images.append(np.array(img))


    return images




def extract_header_info(img: np.ndarray) -> Dict[str, str | None]:
    h, w, _ = img.shape
    crop = img[: int(h * 0.22), :]  # top 22%
    text = pytesseract.image_to_string(crop)


    def find(pattern):
        m = re.search(pattern, text, re.IGNORECASE)
        return m.group(1).strip() if m else None


    return {
        "project_number": find(r"(?:Project|Job)\s*(?:No|#)\s*[:\-]?\s*([A-Z0-9\-]+)"),
        "customer_name": find(r"(?:Customer|Project Name)\s*[:\-]?\s*(.+)"),
        "project_manager": find(r"(?:Project Manager|PM)\s*[:\-]?\s*(.+)"),
    }




def looks_like_writeup_page(img: np.ndarray) -> bool:
    h, _, _ = img.shape
    sample = img[: int(h * 0.18), :]
    text = pytesseract.image_to_string(sample).lower()
    return "rework" in text or "write up" in text or "test comments" in text




def count_eng_mfg(img: np.ndarray) -> Dict[str, int]:
    data = pytesseract.image_to_data(img, output_type=pytesseract.Output.DICT)


    words = data["text"]
    left = data["left"]
    top = data["top"]
    width = data["width"]
    height = data["height"]


    eng_x = None
    mfg_x = None
    header_y = None


    for i, word in enumerate(words):
        if word.lower() == "eng":
            eng_x = left[i] + width[i] / 2
            header_y = top[i]
        elif word.lower() == "mfg":
            mfg_x = left[i] + width[i] / 2
            header_y = top[i] if header_y is None else min(header_y, top[i])


    if eng_x is None or mfg_x is None:
        return {"engineering": 0, "manufacturing": 0}


    rows = {}
    for i, word in enumerate(words):
        if word.upper() == "X":
            y = top[i] + height[i] / 2
            if y <= header_y + 25:
                continue


            row_key = round(y / 20)
            x = left[i] + width[i] / 2
            rows.setdefault(row_key, []).append(x)


    eng = 0
    mfg = 0
    for xs in rows.values():
        avg_x = sum(xs) / len(xs)
        if abs(avg_x - eng_x) < abs(avg_x - mfg_x):
            eng += 1
        else:
            mfg += 1


    return {"engineering": eng, "manufacturing": mfg}




def parse_qc_pdf(pdf_bytes: bytes) -> Dict[str, Any]:
    pages = pdf_to_images(pdf_bytes)
    header = extract_header_info(pages[0])


    totals = {"engineering": 0, "manufacturing": 0}
    for page in pages:
        if looks_like_writeup_page(page):
            counts = count_eng_mfg(page)
            totals["engineering"] += counts["engineering"]
            totals["manufacturing"] += counts["manufacturing"]


    return {
        **header,
        "writeups": totals,
    }
