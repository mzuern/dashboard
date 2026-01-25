from __future__ import annotations


import io
from pathlib import Path
import fitz  # pymupdf
from PIL import Image


def render_pdf_page_to_pil(pdf_path: str | Path, page_index: int = 0, dpi: int = 200) -> Image.Image:
    pdf_path = Path(pdf_path)


    doc = fitz.open(pdf_path)
    try:
        page = doc.load_page(page_index)
        # DPI -> zoom factor (72 is default PDF dpi)
        zoom = dpi / 72.0
        mat = fitz.Matrix(zoom, zoom)
        pix = page.get_pixmap(matrix=mat, alpha=False)


        img_bytes = pix.tobytes("png")
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        return img
    finally:
        doc.close()

def render_pdf_bytes_page_to_pil(pdf_bytes: bytes, page_index: int = 0, dpi: int = 200) -> Image.Image:
    """Render a PDF page from in-memory bytes (UploadFile)."""
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    try:
        page = doc.load_page(page_index)
        zoom = dpi / 72.0
        mat = fitz.Matrix(zoom, zoom)
        pix = page.get_pixmap(matrix=mat, alpha=False)
        img_bytes = pix.tobytes("png")
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        return img
    finally:
        doc.close()
