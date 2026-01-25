from __future__ import annotations
from typing import List, Dict, Optional, Tuple
from PIL import Image

from ocr.ocr_adapter import ocr_image_to_text


def _crop_rel(img: Image.Image, box: Tuple[float, float, float, float]) -> Image.Image:
    w, h = img.size
    x1, y1, x2, y2 = box
    return img.crop((int(w*x1), int(h*y1), int(w*x2), int(h*y2)))


def read_issue_row_texts(
    page_img: Image.Image,
    *,
    comments_box=(0.15, 0.22, 0.76, 0.88),
    rows: int = 12
) -> List[str]:
    """
    OCR the comments area, sliced into row bands.
    Returns list of text per row index.
    """
    comments = _crop_rel(page_img, comments_box)
    w, h = comments.size
    row_h = max(1, h // rows)

    texts: List[str] = []
    for r in range(rows):
        y1 = r * row_h
        y2 = (r + 1) * row_h if r < rows - 1 else h
        row_img = comments.crop((0, y1, w, y2))
        t = ocr_image_to_text(row_img).strip()
        texts.append(t)
    return texts
