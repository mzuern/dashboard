from pydantic import BaseModel
from typing import Optional, List

class ProjectOut(BaseModel):
    id: int
    name: str
    class Config:
        from_attributes = True

class DeviceOut(BaseModel):
    id: int
    project_id: int
    tag: str
    description: Optional[str] = None
    class Config:
        from_attributes = True

class HotspotOut(BaseModel):
    id: int
    drawing_id: int
    device_id: int
    x: int
    y: int
    w: int
    h: int
    label: Optional[str] = None
    class Config:
        from_attributes = True

class DrawingOut(BaseModel):
    id: int
    project_id: int
    title: str
    image_url: str
    hotspots: List[HotspotOut]
    class Config:
        from_attributes = True

class IssueCreate(BaseModel):
    project_id: int
    device_id: int
    drawing_id: Optional[int] = None
    severity: str = "medium"
    notes: Optional[str] = None

class IssueOut(BaseModel):
    id: int
    project_id: int
    device_id: int
    drawing_id: Optional[int] = None
    severity: str
    status: str
    notes: Optional[str] = None
    class Config:
        from_attributes = True

class TestLine(BaseModel):
    device_id: int
    tag: str
    description: Optional[str] = None
    has_open_issue: bool
    class Config:
        from_attributes = True

class TestSheetOut(BaseModel):
    project_id: int
    lines: List[TestLine]
