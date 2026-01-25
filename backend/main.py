from __future__ import annotations

import fitz

from contextlib import asynccontextmanager
from pathlib import Path
from typing import Dict, List, Optional


import pytesseract
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session, selectinload
from sqlalchemy import func


from db import Base, SessionLocal, engine
from models import Device, Drawing, Issue, Project
from ocr_config import TESSERACT_CMD
from ocr.pdf_render import render_pdf_page_to_pil, render_pdf_bytes_page_to_pil
from ocr.header_extract import extract_page1_header
from ocr.ocr_adapter import ocr_header_image
from ocr.page_discovery import discover_issue_table_pages
from ocr.pdf_render import render_pdf_path_page_to_pil
from ocr.mark_reader import read_eng_mfg_marks
from ocr.issue_text_reader import read_issue_row_texts
from schemas import (
    DrawingOut,
    IssueCreate,
    IssueOut,
    ProjectOut,
    TestLine,
    TestSheetOut,
)


# IMPORTANT: pick ONE parser import
# If your real parser lives in qc_parser.py, keep this:
from qc_parser import parse_qc_pdf




# Optional OCR deps:
#   pip install opencv-python numpy python-multipart
try:
    import cv2  # type: ignore
    import numpy as np  # type: ignore
except Exception:
    cv2 = None  # type: ignore
    np = None  # type: ignore




# -------------------------
# Lifespan (startup/shutdown)
# -------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD
    yield




app = FastAPI(title="Dashboard Demo API", lifespan=lifespan)


# -------------------------
# CORS (Vite dev)
# -------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)




# -------------------------
# DB dependency
# -------------------------
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()




# -------------------------
# Health
# -------------------------
@app.get("/health")
def health():
    return {"ok": True}


# =========================
# Dashboard Summary (UI expects this)
# =========================
@app.get("/dashboard/projects")
def dashboard_projects():
    """Return a lightweight project summary for the QC Metrics Dashboard UI.

    This endpoint exists because the React dashboard calls /dashboard/projects.
    Until you wire real DB-backed QC/issue data, we generate summaries from the
    sample PDFs in backend/data/incoming_pdfs by extracting the page-1 header.
    """

    if not INCOMING_PDF_DIR.exists():
        return []

    rows = []
    for idx, pdf_path in enumerate(sorted(INCOMING_PDF_DIR.glob("*.pdf")), start=1):
        try:
            img = render_pdf_page_to_pil(pdf_path, page_index=0, dpi=200)
            header = extract_page1_header(img, ocr_func=ocr_header_image)
        except Exception:
            header = {}

        project_number = (
            header.get("job_number")
            or header.get("project_number")
            or pdf_path.stem.split("_")[0]
            or str(idx)
        )

        rows.append(
            {
                "project_id": idx,
                "project_number": str(project_number),
                "customer_name": header.get("project_name") or "(from PDF header)",
                "project_manager": header.get("project_manager") or "(unknown)",
                "mfg_issue_count": 0,
                "eng_issue_count": 0,
                "open_issue_count": 0,
                "closed_issue_count": 0,
                "oldest_open_days": None,
            }
        )

    return rows




# =========================
# Projects
# =========================
@app.get("/projects", response_model=List[ProjectOut])
def list_projects(db: Session = Depends(get_db)):
    return db.query(Project).order_by(Project.id.desc()).all()




# =========================
# Drawings
# =========================
@app.get("/projects/{project_id}/drawings", response_model=List[DrawingOut])
def list_drawings(project_id: int, db: Session = Depends(get_db)):
    drawings = (
        db.query(Drawing)
        .options(selectinload(Drawing.hotspots))
        .filter(Drawing.project_id == project_id)
        .order_by(Drawing.id.desc())
        .all()
    )
    return drawings




@app.get("/drawings/{drawing_id}", response_model=DrawingOut)
def get_drawing(drawing_id: int, db: Session = Depends(get_db)):
    drawing = (
        db.query(Drawing)
        .options(selectinload(Drawing.hotspots))
        .filter(Drawing.id == drawing_id)
        .first()
    )
    if not drawing:
        raise HTTPException(status_code=404, detail="Drawing not found")
    return drawing




# =========================
# Issues
# =========================
@app.get("/projects/{project_id}/issues", response_model=List[IssueOut])
def list_issues(project_id: int, db: Session = Depends(get_db)):
    return (
        db.query(Issue)
        .filter(Issue.project_id == project_id)
        .order_by(Issue.id.desc())
        .all()
    )




@app.post("/issues", response_model=IssueOut)
def create_issue(payload: IssueCreate, db: Session = Depends(get_db)):
    if payload.project_id is None:
        raise HTTPException(status_code=400, detail="project_id is required")


    issue = Issue(
        project_id=payload.project_id,
        device_id=payload.device_id,
        drawing_id=payload.drawing_id,
        severity=payload.severity,
        status="open",
        notes=payload.notes,
    )
    db.add(issue)
    db.commit()
    db.refresh(issue)
    return issue




@app.patch("/issues/{issue_id}", response_model=IssueOut)
def patch_issue(issue_id: int, payload: Dict[str, Optional[str]], db: Session = Depends(get_db)):
    issue = db.query(Issue).filter(Issue.id == issue_id).first()
    if not issue:
        raise HTTPException(status_code=404, detail="Issue not found")


    if "status" in payload and payload["status"]:
        issue.status = payload["status"]
    if "severity" in payload and payload["severity"]:
        issue.severity = payload["severity"]
    if "notes" in payload:
        issue.notes = payload["notes"]


    db.commit()
    db.refresh(issue)
    return issue




# =========================
# Devices (BOM-ish) + Device Issues
# =========================
@app.get("/projects/{project_id}/devices")
def list_devices(project_id: int, db: Session = Depends(get_db)):
    devices = (
        db.query(Device)
        .filter(Device.project_id == project_id)
        .order_by(Device.tag.asc())
        .all()
    )


    counts = dict(
        db.query(Issue.device_id, func.count(Issue.id))
        .filter(
            Issue.project_id == project_id,
            Issue.status == "open",
            Issue.device_id.isnot(None),
        )
        .group_by(Issue.device_id)
        .all()
    )


    return [
        {
            "id": d.id,
            "project_id": d.project_id,
            "tag": d.tag,
            "description": d.description,
            "open_issues": int(counts.get(d.id, 0)),
        }
        for d in devices
    ]




@app.get("/devices/{device_id}")
def get_device(device_id: int, db: Session = Depends(get_db)):
    d = db.query(Device).filter(Device.id == device_id).first()
    if not d:
        raise HTTPException(status_code=404, detail="Device not found")
    return {
        "id": d.id,
        "project_id": d.project_id,
        "tag": d.tag,
        "description": d.description,
    }




@app.get("/devices/{device_id}/issues", response_model=List[IssueOut])
def get_device_issues(device_id: int, db: Session = Depends(get_db)):
    return (
        db.query(Issue)
        .filter(Issue.device_id == device_id)
        .order_by(Issue.id.desc())
        .all()
    )




# =========================
# Testsheet (Device list + open issue markers)
# =========================


@app.get("/ingest/discover")
def ingest_discover(file: str):
    pdf_path = f"backend/data/incoming_pdfs/{file}"
    try:
        doc = fitz.open(pdf_path)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"PDF not found or unreadable: {e}")

    result = discover_issue_table_pages(pdf_path, doc.page_count)
    doc.close()
    return {"file": file, **result}


@app.get("/ingest/issues/rows")
def ingest_issue_rows(file: str):
    """
    Returns row-by-row Eng/Mfg marks + (optional) OCR text for each row,
    across all discovered issue table pages.
    """
    pdf_path = f"backend/data/incoming_pdfs/{file}"
    try:
        doc = fitz.open(pdf_path)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"PDF not found or unreadable: {e}")

    discovered = discover_issue_table_pages(pdf_path, doc.page_count)
    pages = discovered["issue_table_pages"]

    all_rows = []
    for p in pages:
        img = render_pdf_path_page_to_pil(pdf_path, page_index=p, dpi=250)

        marks = read_eng_mfg_marks(img, rows=12)
        texts = read_issue_row_texts(img, rows=12)

        for m in marks:
            txt = texts[m.row_index] if m.row_index < len(texts) else ""
            # Only keep rows that have something (either mark or text)
            if (m.eng_marked or m.mfg_marked) or (txt and len(txt) > 2):
                all_rows.append({
                    "page": p,
                    "row": m.row_index,
                    "engineering": m.eng_marked,
                    "manufacturing": m.mfg_marked,
                    "eng_ink": m.eng_ratio,
                    "mfg_ink": m.mfg_ratio,
                    "text": txt
                })

    doc.close()
    return {
        "file": file,
        "pages": pages,
        "rows": all_rows
    }



@app.get("/projects/{project_id}/testsheets", response_model=TestSheetOut)
def get_testsheet(project_id: int, db: Session = Depends(get_db)):
    devices = (
        db.query(Device)
        .filter(Device.project_id == project_id)
        .order_by(Device.id.asc())
        .all()
    )


    open_issues = (
        db.query(Issue)
        .filter(Issue.project_id == project_id, Issue.status == "open")
        .all()
    )
    open_device_ids = {i.device_id for i in open_issues if i.device_id is not None}


    lines = [
        TestLine(
            device_id=d.id,
            tag=d.tag,
            description=d.description,
            has_open_issue=(d.id in open_device_ids),
        )
        for d in devices
    ]


    return TestSheetOut(project_id=project_id, lines=lines)




# =========================
# OCR helpers
# =========================
INCOMING_PDF_DIR = Path(__file__).resolve().parent / "data" / "incoming_pdfs"




@app.get("/ingest/page1")
def ingest_page1(file: str = Query(..., description="PDF filename inside backend/data/incoming_pdfs")):
    pdf_path = INCOMING_PDF_DIR / file
    if not pdf_path.exists():
        raise HTTPException(status_code=404, detail=f"File not found: {pdf_path.name}")


    img = render_pdf_page_to_pil(pdf_path, page_index=0, dpi=200)
    data = extract_page1_header(img, ocr_func=ocr_header_image)
    data["source_pdf"] = file
    return data



@app.get("/ingest/files")
def ingest_list_files():
    """List sample PDFs already in backend/data/incoming_pdfs."""
    if not INCOMING_PDF_DIR.exists():
        return {"files": []}
    files = sorted([p.name for p in INCOMING_PDF_DIR.glob("*.pdf")])
    return {"files": files}


@app.post("/ingest/page1/upload")
async def ingest_page1_upload(file: UploadFile = File(...)):
    """Upload a PDF and extract the page-1 header fields."""
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty upload")

    try:
        img = render_pdf_bytes_page_to_pil(data, page_index=0, dpi=200)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to render PDF: {e}")

    header = extract_page1_header(img, ocr_func=ocr_header_image)
    header["source_pdf"] = file.filename
    return header



@app.post("/ocr")
async def ocr_image(file: UploadFile = File(...)):
    if cv2 is None or np is None:
        raise HTTPException(
            status_code=500,
            detail="OCR deps missing. Install: pip install opencv-python numpy python-multipart",
        )


    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty upload")


    img = np.frombuffer(data, np.uint8)
    bgr = cv2.imdecode(img, cv2.IMREAD_COLOR)
    if bgr is None:
        raise HTTPException(status_code=400, detail="Invalid image")


    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, 9, 75, 75)
    gray = cv2.normalize(gray, None, 0, 255, cv2.NORM_MINMAX)
    thr = cv2.adaptiveThreshold(
        gray, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31, 10
    )


    ocr = pytesseract.image_to_data(thr, output_type=pytesseract.Output.DICT)


    grouped: Dict[int, Dict[str, List]] = {}
    n = len(ocr.get("text", []))


    for i in range(n):
        text = (ocr["text"][i] or "").strip()
        if not text:
            continue


        line_num = int(ocr.get("line_num", [0])[i])
        conf_raw = ocr.get("conf", ["-1"])[i]


        try:
            conf = float(conf_raw)
        except Exception:
            conf = -1.0


        if line_num not in grouped:
            grouped[line_num] = {"tokens": [], "confs": []}


        grouped[line_num]["tokens"].append(text)
        if conf >= 0:
            grouped[line_num]["confs"].append(conf)


    REVIEW_THRESHOLD = 60.0
    lines = []


    for line_num, v in grouped.items():
        joined = " ".join(v["tokens"]).strip()
        avg_conf = (sum(v["confs"]) / len(v["confs"])) if v["confs"] else 0.0
        confidence = round(float(avg_conf), 1)


        lines.append({
            "line": line_num,
            "text": joined,
            "confidence": confidence,
            "needs_review": confidence < REVIEW_THRESHOLD,
        })


    lines.sort(key=lambda x: x["confidence"])
    return JSONResponse({"lines": lines})




# =========================
# QC PDF parse (single endpoint)
# =========================
@app.post("/qc/parse")
async def qc_parse(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty upload")


    try:
        result = parse_qc_pdf(data)
        return JSONResponse(result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

