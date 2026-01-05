from typing import List, Optional
from pydantic import BaseModel


# --------------------
# Base mixin
# --------------------
class ORMBase(BaseModel):
    model_config = {
        "from_attributes": True
    }


# --------------------
# Projects
# --------------------
class ProjectOut(ORMBase):
    id: int
    name: str


# --------------------
# Devices
# --------------------
class DeviceOut(ORMBase):
    id: int
    project_id: int
    tag: str
    description: Optional[str] = None


# --------------------
# Drawings / Hotspots
# --------------------
class HotspotOut(ORMBase):
    id: int
    drawing_id: int
    device_id: int
    x: int
    y: int
    w: int
    h: int
    label: Optional[str] = None


class DrawingOut(ORMBase):
    id: int
    project_id: int
    title: str
    image_url: str
    hotspots: List[HotspotOut] = []


# --------------------
# Issues
# --------------------
class IssueCreate(BaseModel):
    project_id: int
    device_id: Optional[int] = None
    drawing_id: Optional[int] = None
    severity: str = "medium"
    notes: Optional[str] = None


class IssueFromOCR(BaseModel):
    project_id: int
    text: str
    device_id: Optional[int] = None
    drawing_id: Optional[int] = None
    severity: str = "medium"


class IssueOut(ORMBase):
    id: int
    project_id: int
    device_id: Optional[int] = None
    drawing_id: Optional[int] = None
    severity: str
    status: str
    notes: Optional[str] = None


# --------------------
# Test Sheets
# --------------------
class TestLine(ORMBase):
    device_id: int
    tag: str
    description: Optional[str] = None
    has_open_issue: bool

class TestSheetOut(BaseModel):
    project_id: int
    lines: List[TestLine]