from sqlalchemy import Column, Integer, String, ForeignKey, Float, Text, DateTime
from sqlalchemy.orm import relationship
from datetime import datetime, timezone
from db import Base

class Project(Base):
    __tablename__ = "projects"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)

    devices = relationship("Device", back_populates="project", cascade="all, delete")
    drawings = relationship("Drawing", back_populates="project", cascade="all, delete")
    issues = relationship("Issue", back_populates="project", cascade="all, delete")


class Device(Base):
    __tablename__ = "devices"
    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id"), nullable=False)
    tag = Column(String, nullable=False)  # e.g. "K1", "TS3", "SEL-751"
    description = Column(String, nullable=True)

    project = relationship("Project", back_populates="devices")
    hotspots = relationship("Hotspot", back_populates="device", cascade="all, delete")
    issues = relationship("Issue", back_populates="device", cascade="all, delete")


class Drawing(Base):
    __tablename__ = "drawings"
    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id"), nullable=False)
    title = Column(String, nullable=False)
    image_url = Column(String, nullable=False)  # for demo, a URL

    project = relationship("Project", back_populates="drawings")
    hotspots = relationship("Hotspot", back_populates="drawing", cascade="all, delete")


class Hotspot(Base):
    __tablename__ = "hotspots"
    id = Column(Integer, primary_key=True, index=True)
    drawing_id = Column(Integer, ForeignKey("drawings.id"), nullable=False)
    device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)

    # store as % values so it works on any screen size
    x = Column(Integer, nullable=False)  # 0-10000 (represents 0.00% to 100.00%)
    y = Column(Integer, nullable=False)
    w = Column(Integer, nullable=False)
    h = Column(Integer, nullable=False)

    label = Column(String, nullable=True)

    drawing = relationship("Drawing", back_populates="hotspots")
    device = relationship("Device", back_populates="hotspots")


class Issue(Base):
    __tablename__ = "issues"
    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id"), nullable=False)
    device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    drawing_id = Column(Integer, ForeignKey("drawings.id"), nullable=True)

    severity = Column(String, nullable=False, default="medium")  # low/medium/high
    status = Column(String, nullable=False, default="open")      # open/closed
    notes = Column(Text, nullable=True)

    project = relationship("Project", back_populates="issues")
    device = relationship("Device", back_populates="issues")

class QCPdfSummary(Base):
    __tablename__ = "qc_pdf_summaries"


    id = Column(Integer, primary_key=True, index=True)


    # filename as stored in backend/data/incoming_pdfs
    source_pdf = Column(String, unique=True, index=True, nullable=False)


    # parsed metadata
    project_number = Column(String, index=True, nullable=True)
    customer_name = Column(String, nullable=True)
    project_manager = Column(String, nullable=True)


    # metrics
    mfg_issue_count = Column(Integer, default=0, nullable=False)
    eng_issue_count = Column(Integer, default=0, nullable=False)
    open_issue_count = Column(Integer, default=0, nullable=False)
    closed_issue_count = Column(Integer, default=0, nullable=False)
    oldest_open_days = Column(Float, nullable=True)


    # ingest status
    parse_status = Column(String, default="ok", nullable=False)  # ok | error
    parse_error = Column(Text, nullable=True)


    ingested_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

