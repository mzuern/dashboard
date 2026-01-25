"""Tesseract configuration.

This repo runs on both Windows (dev) and Linux (deploy).

Priority order:
  1) If you set env var TESSERACT_CMD, we use that.
  2) Otherwise, we try common Windows install paths.
  3) Otherwise, we fall back to just 'tesseract' (expects it on PATH).

PowerShell example:
  $env:TESSERACT_CMD = 'C:\\Program Files\\Tesseract-OCR\\tesseract.exe'

Bash example:
  export TESSERACT_CMD=/usr/bin/tesseract
"""

from __future__ import annotations

import os
from pathlib import Path


def _first_existing(paths: list[str]) -> str | None:
    for p in paths:
        try:
            if p and Path(p).exists():
                return p
        except Exception:
            continue
    return None


# Allow user override
TESSERACT_CMD = os.getenv("TESSERACT_CMD")

if not TESSERACT_CMD:
    TESSERACT_CMD = _first_existing(
        [
            r"C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
            r"C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
            # Your prior hard-coded path (keep as a last-resort convenience)
            r"C:\\Users\\306051\\AppData\\Local\\Programs\\Tesseract-OCR\\tesseract.exe",
        ]
    )

# Final fallback: hope it's on PATH
if not TESSERACT_CMD:
    TESSERACT_CMD = "tesseract"
