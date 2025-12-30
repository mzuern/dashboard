from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from db import SessionLocal, Base, engine
from models import Project, Drawing, Hotspot, Issue, Device
from schemas import ProjectOut, DrawingOut, IssueCreate, IssueOut, TestSheetOut, TestLine
from typing import List

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Dashboard Demo API")

# Allow local dev web app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/projects", response_model=List[ProjectOut])
def list_projects(db: Session = Depends(get_db)):
    return db.query(Project).all()

@app.get("/projects/{project_id}/drawings", response_model=List[DrawingOut])
def list_drawings(project_id: int, db: Session = Depends(get_db)):
    drawings = db.query(Drawing).filter(Drawing.project_id == project_id).all()
    # ensure hotspots load
    for d in drawings:
        _ = d.hotspots
    return drawings

@app.get("/drawings/{drawing_id}", response_model=DrawingOut)
def get_drawing(drawing_id: int, db: Session = Depends(get_db)):
    d = db.query(Drawing).filter(Drawing.id == drawing_id).first()
    if not d:
        return None  # demo simplicity
    _ = d.hotspots
    return d

@app.get("/projects/{project_id}/issues", response_model=List[IssueOut])
def list_issues(project_id: int, db: Session = Depends(get_db)):
    return db.query(Issue).filter(Issue.project_id == project_id).order_by(Issue.id.desc()).all()

@app.post("/issues", response_model=IssueOut)
def create_issue(payload: IssueCreate, db: Session = Depends(get_db)):
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

@app.get("/projects/{project_id}/testsheets", response_model=TestSheetOut)
def get_testsheet(project_id: int, db: Session = Depends(get_db)):
    devices = db.query(Device).filter(Device.project_id == project_id).all()
    open_issues = db.query(Issue).filter(Issue.project_id == project_id, Issue.status == "open").all()
    open_device_ids = {i.device_id for i in open_issues}

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
