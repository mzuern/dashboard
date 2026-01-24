# backend/ocr/header_extract.py
from __future__ import annotations


import re
from dataclasses import dataclass
from typing import Any
from PIL import Image


@dataclass
class HeaderFields:
    project_number: str | None = None
    project_name: str | None = None
    project_manager: str | None = None
    date: str | None = None


def crop_header_band(img: Image.Image) -> Image.Image:
    """
    Crop the top header band where Project No / Project Name / Date / PM live.
    Adjust ratios once for the template and you're done.
    """
    w, h = img.size
    top = 0
    bottom = int(h * 0.18)  # ~top 18% (tune if needed)
    return img.crop((0, top, w, bottom))


def normalize_text(s: str) -> str:
    s = s.replace("\n", " ")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def parse_header_from_text(text: str) -> HeaderFields:
    t = normalize_text(text)


    # Common OCR quirks:
    # - "Date:" sometimes becomes "ate:"
    # - extra spaces / missing punctuation
    # - "Project No" vs "Project No:" vs "Project No."
    fields = HeaderFields()


    # Project No
    m = re.search(r"(Project\s*No\.?\s*:?\s*)(\d{3,10})", t, re.IGNORECASE)
    if m:
        fields.project_number = m.group(2)


    # Date (accept Date or ate)
    m = re.search(r"((Date|ate)\s*:?\s*)(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4})", t, re.IGNORECASE)
    if m:
        fields.date = m.group(3)


    # Project Manager
    m = re.search(r"(Project\s*Manager\s*:?\s*)([A-Za-z]+(?:\s+[A-Za-z]+){1,3})", t, re.IGNORECASE)
    if m:
        fields.project_manager = m.group(2).strip()


    # Project Name (grab chunk after "Project Name:" up to Date/PM if possible)
    m = re.search(r"(Project\s*Name\s*:?\s*)(.+)", t, re.IGNORECASE)
    if m:
        tail = m.group(2)


        # stop at Date / Project Manager if they appear after
        tail = re.split(r"(Date\s*:|ate\s*:|Project\s*Manager\s*:)", tail, flags=re.IGNORECASE)[0]
        tail = tail.strip(" -_:/")
        tail = re.sub(r"\s+", " ", tail).strip()


        # light cleanup
        if tail:
            fields.project_name = tail


    return fields


def extract_page1_header(img: Image.Image, ocr_func) -> dict:
    """
    ocr_func(header_img) should return either:
      - a list of strings, OR
      - a dict containing 'rec_texts' (like your PaddleX output)
    """
    header_img = crop_header_band(img)


    raw = ocr_func(header_img)


    # Accept multiple formats
    if isinstance(raw, dict) and "rec_texts" in raw:
        lines = [x for x in raw["rec_texts"] if isinstance(x, str)]
        joined = " ".join(lines)
    elif isinstance(raw, list):
        joined = " ".join([str(x) for x in raw])
    else:
        joined = str(raw)


    fields = parse_header_from_text(joined)


    return {
        "project_number": fields.project_number,
        "project_name": fields.project_name,
        "project_manager": fields.project_manager,
        "date": fields.date,
        "raw_text": joined,  # keep this for debugging; remove later if desired
    }