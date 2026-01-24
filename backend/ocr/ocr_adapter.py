from __future__ import annotations


import numpy as np
from PIL import Image


# If this import fails, run:
#   pip install paddleocr paddlepaddle
from paddleocr import PaddleOCR


# Create once (global) so FastAPI doesn't reload models per request
_OCR = PaddleOCR(lang="en", use_textline_orientation=True)


def ocr_header_image_paddle(img: Image.Image) -> dict:
    """
    Returns a dict with 'rec_texts' like your earlier debug dump.
    """
    arr = np.array(img)  # RGB
    result = _OCR.predict(arr)


    # Different versions return different shapes; handle common cases:
    # We want a flat list of recognized strings.
    rec_texts = []


    # If it's list of dicts
    if isinstance(result, list) and len(result) > 0 and isinstance(result[0], dict):
        # some pipelines return dict with keys including 'rec_texts'
        d0 = result[0]
        if "rec_texts" in d0:
            rec_texts = d0["rec_texts"]
        else:
            # fallback: attempt to find text entries
            for k, v in d0.items():
                if k.lower().endswith("texts") and isinstance(v, list):
                    rec_texts = v
                    break
    else:
        # Last resort: stringify
        rec_texts = [str(result)]


    return {"rec_texts": rec_texts}

