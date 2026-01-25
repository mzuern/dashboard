from __future__ import annotations

from typing import Any, Dict, List, Literal, Callable

import numpy as np
from PIL import Image
import pytesseract

from ocr_config import TESSERACT_CMD

_PADDLE_AVAILABLE = False
_PADDLE_OCR = None

try:
    from paddleocr import PaddleOCR  # type: ignore
    _PADDLE_OCR = PaddleOCR(lang='en', use_textline_orientation=True)
    _PADDLE_AVAILABLE = True
except Exception:
    _PADDLE_AVAILABLE = False
    _PADDLE_OCR = None


def _as_lines(s: str) -> List[str]:
    return [ln.strip() for ln in s.splitlines() if ln.strip()]


def _ocr_with_paddle(img: Image.Image) -> List[str]:
    assert _PADDLE_OCR is not None
    arr = np.array(img)
    result: Any = _PADDLE_OCR.predict(arr)

    # Different PaddleOCR versions return different shapes.
    if isinstance(result, list) and result and isinstance(result[0], dict):
        d0 = result[0]
        if 'rec_texts' in d0 and isinstance(d0['rec_texts'], list):
            return [str(x) for x in d0['rec_texts'] if str(x).strip()]

    # Fallback: stringify whatever we got
    return _as_lines(str(result))


def _ocr_with_tesseract(img: Image.Image) -> List[str]:
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD
    # psm 6 = assume a uniform block of text
    txt = pytesseract.image_to_string(img, config='--psm 6')
    return _as_lines(txt)


def ocr_header_image(
    img: Image.Image,
    prefer: Literal['paddle', 'tesseract', 'auto'] = 'auto',
) -> Dict[str, Any]:
    # prefer='auto' means: use PaddleOCR if available, otherwise tesseract
    if prefer == 'paddle':
        if not _PADDLE_AVAILABLE:
            raise RuntimeError('PaddleOCR is not installed. Install paddleocr and paddlepaddle, or use prefer=tesseract.')
        rec_texts = _ocr_with_paddle(img)
        return {'rec_texts': rec_texts, 'engine': 'paddle'}

    if prefer == 'tesseract':
        rec_texts = _ocr_with_tesseract(img)
        return {'rec_texts': rec_texts, 'engine': 'tesseract'}

    # auto
    if _PADDLE_AVAILABLE:
        try:
            rec_texts = _ocr_with_paddle(img)
            return {'rec_texts': rec_texts, 'engine': 'paddle'}
        except Exception:
            pass

    rec_texts = _ocr_with_tesseract(img)
    return {'rec_texts': rec_texts, 'engine': 'tesseract'}

def ocr_image_to_text(pil_image: Image.Image) -> str:
    """
    Single stable OCR entrypoint for the rest of the app.
    It calls the OCR function that this repo actually provides.

    We only reference names after checking they exist (keeps Pylance happy).
    """
    # 1) Preferred: you already have a function like ocr_header_image(img) -> str
    fn: Callable[[Image.Image], Any] | None = globals().get("ocr_header_image")  # type: ignore
    if callable(fn):
        out = fn(pil_image)
        return out if isinstance(out, str) else str(out)

    # 2) Alternative: maybe you have ocr_pil_image(img) -> str
    fn = globals().get("ocr_pil_image")  # type: ignore
    if callable(fn):
        out = fn(pil_image)
        return out if isinstance(out, str) else str(out)

    # 3) As a last resort, if there is a configured engine object with a method
    engine = globals().get("OCR_ENGINE") or globals().get("ocr_engine")  # type: ignore
    if engine is not None:
        meth = getattr(engine, "image_to_text", None)
        if callable(meth):
            out = meth(pil_image)
            return out if isinstance(out, str) else str(out)

    raise RuntimeError(
        "ocr_image_to_text: No OCR callable found. "
        "Expected one of: ocr_header_image(img), ocr_pil_image(img), "
        "or OCR_ENGINE/ocr_engine.image_to_text(img)."
    )