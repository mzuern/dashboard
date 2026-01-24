from __future__ import annotations
from qc_extract import parse_qc_pdf
from qc_parser import parse_qc_pdf
from contextlib import asynccontextmanager
from typing import Dict, List, Optional

import pytesseract
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session, selectinload
from sqlalchemy import func

from db import Base, SessionLocal, engine
from models import Device, Drawing, Issue, Project
from ocr_config import TESSERACT_CMD
from schemas import (
    DrawingOut,
    IssueCreate,
    IssueOut,
    ProjectOut,
    TestLine,
    TestSheetOut,
)

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
    # Startup: create tables + configure tesseract
    Base.metadata.create_all(bind=engine)
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD
    yield
    # Shutdown: nothing yet


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

# =========================
# Devices (BOM-ish) + Device Issues (for Device Panel)
# =========================

@app.get("/projects/{project_id}/devices")
def list_devices(project_id: int, db: Session = Depends(get_db)):
    """
    Returns devices for a project + open issue counts.
    UI uses this for BOM list + badges.
    """
    devices = (
        db.query(Device)
        .filter(Device.project_id == project_id)
        .order_by(Device.tag.asc())
        .all()
    )

    # open issue counts per device
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
    """
    Returns issues for dropdown in Device Panel.
    """
    return (
        db.query(Issue)
        .filter(Issue.device_id == device_id)
        .order_by(Issue.id.desc())
        .all()
    )


@app.patch("/issues/{issue_id}", response_model=IssueOut)
def patch_issue(issue_id: int, payload: Dict[str, Optional[str]], db: Session = Depends(get_db)):
    """
    Minimal patch endpoint so UI can close/reopen issues.
    Accepts keys: status, severity, notes
    """
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
# Testsheet (Device list + open issue markers)
# =========================
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
# OCR (scanner-ish preprocessing + line confidences)
# =========================
@app.post("/ocr")
async def ocr_image(file: UploadFile = File(...)):
    """
    Upload an image and get extracted "lines" with confidence.
    Requires:
      pip install opencv-python numpy python-multipart
    """
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


    # --- scanner-ish preprocessing ---
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, 9, 75, 75)
    gray = cv2.normalize(gray, None, 0, 255, cv2.NORM_MINMAX)
    thr = cv2.adaptiveThreshold(
        gray, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31, 10
    )


    # OCR tokens with confidence
    ocr = pytesseract.image_to_data(thr, output_type=pytesseract.Output.DICT)


    # Group tokens into simple "lines" using line_num
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


    # ---- build response ----
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


    # Lowest confidence first for UI highlighting
    lines.sort(key=lambda x: x["confidence"])


    return JSONResponse({"lines": lines})

@app.post("/qc/parse_pdf")
async def qc_parse_pdf(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty upload")
    try:
        result = parse_qc_pdf(data)
        return JSONResponse(result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    
@app.post("/qc/parse")
async def qc_parse(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        return {"error": "Empty file"}
    return parse_qc_pdf(data)