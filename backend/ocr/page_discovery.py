from __future__ import annotations
from dataclasses import dataclass
from typing import List, Dict, Tuple
import re

from ocr.pdf_render import render_pdf_path_page_to_pil
from ocr.ocr_adapter import ocr_image_to_text


@dataclass
class PageHit:
    page_index: int
    score: float
    text: str


def _norm(s: str) -> str:
    s = s.lower()
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def discover_issue_table_pages(pdf_path: str, page_count: int) -> Dict[str, List[int]]:
    """
    Finds pages that contain the 'Test Comments and/or Rework Required' table with Eng/Mfg columns.
    Does NOT assume page numbers.

    Strategy:
      - OCR only the top-right / header band (cheap)
      - score based on keywords: Eng, Mfg, Dept, Corrected, Retested, Rework Required
    """
    hits: List[PageHit] = []

    for i in range(page_count):
        img = render_pdf_path_page_to_pil(pdf_path, page_index=i, dpi=200)

        # Crop area where those headers live (top band, right-ish)
        w, h = img.size
        crop = img.crop((
            int(w * 0.05),
            int(h * 0.05),
            int(w * 0.98),
            int(h * 0.35),
        ))

        text = ocr_image_to_text(crop)
        t = _norm(text)

        score = 0.0
        # Strong anchors
        if "eng" in t: score += 2.0
        if "mfg" in t: score += 2.0
        if "dept" in t: score += 1.0
        if "corrected" in t: score += 1.0
        if "retested" in t: score += 1.0
        if "rework required" in t or "test comments" in t: score += 2.0

        # Avoid false matches on other pages
        if "testing checklist" in t: score += 0.5  # still valid, your table is inside the checklist page
        if "instruction" in t: score += 0.3

        if score >= 4.0:
            hits.append(PageHit(i, score, text))

    # Sort by confidence
    hits.sort(key=lambda x: x.score, reverse=True)

    return {
        "issue_table_pages": [h.page_index for h in hits],
        "debug": [{"page": h.page_index, "score": h.score} for h in hits[:10]],
    }
