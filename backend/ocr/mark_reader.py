from __future__ import annotations
from dataclasses import dataclass
from typing import List, Tuple, Dict, Optional

import numpy as np
import cv2
from PIL import Image


@dataclass
class RowMark:
    row_index: int
    eng_marked: bool
    mfg_marked: bool
    eng_ratio: float
    mfg_ratio: float


def _to_bw(img: Image.Image) -> np.ndarray:
    """PIL -> OpenCV gray -> threshold"""
    arr = np.array(img.convert("L"))
    # Adaptive threshold handles scan variations
    bw = cv2.adaptiveThreshold(
        arr, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        21, 10
    )
    return bw


def _ink_ratio(bw: np.ndarray) -> float:
    # bw is 0/255 inverted (ink is 255)
    ink = (bw > 0).sum()
    total = bw.size
    return float(ink) / float(total)


def _crop_rel(img: Image.Image, box: Tuple[float, float, float, float]) -> Image.Image:
    """box is (x1,y1,x2,y2) in relative coords 0..1"""
    w, h = img.size
    x1, y1, x2, y2 = box
    return img.crop((int(w*x1), int(h*y1), int(w*x2), int(h*y2)))


def read_eng_mfg_marks(
    page_img: Image.Image,
    *,
    table_box=(0.05, 0.22, 0.98, 0.88),
    eng_col=(0.76, 0.22, 0.81, 0.88),
    mfg_col=(0.81, 0.22, 0.86, 0.88),
    rows: int = 12,
    mark_threshold: float = 0.015
) -> List[RowMark]:
    """
    Reads whether each row has ink in Eng and/or Mfg column boxes.

    - table_box: roughly where the rows live
    - eng_col/mfg_col: relative x ranges for Eng/Mfg columns (same y as table_box)
    - rows: how many lines/rows to split into
    - mark_threshold: ink ratio threshold to treat as "marked"

    IMPORTANT: These relative boxes are easy to tune once using a full-page sample.
    """
    # Crop only the table region first (faster and more stable)
    table = _crop_rel(page_img, table_box)

    # Now crop Eng/Mfg columns from the original page using absolute rel coords
    eng = _crop_rel(page_img, eng_col)
    mfg = _crop_rel(page_img, mfg_col)

    eng_bw = _to_bw(eng)
    mfg_bw = _to_bw(mfg)

    # Split into row bands
    h = eng_bw.shape[0]
    row_h = max(1, h // rows)

    out: List[RowMark] = []
    for r in range(rows):
        y1 = r * row_h
        y2 = (r + 1) * row_h if r < rows - 1 else h

        eng_slice = eng_bw[y1:y2, :]
        mfg_slice = mfg_bw[y1:y2, :]

        e_ratio = _ink_ratio(eng_slice)
        m_ratio = _ink_ratio(mfg_slice)

        out.append(RowMark(
            row_index=r,
            eng_marked=e_ratio >= mark_threshold,
            mfg_marked=m_ratio >= mark_threshold,
            eng_ratio=e_ratio,
            mfg_ratio=m_ratio
        ))

    return out
