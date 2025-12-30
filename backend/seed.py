from db import Base, engine, SessionLocal
from models import Project, Device, Drawing, Hotspot, Issue

def reset_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

def seed():
    reset_db()
    db = SessionLocal()
    try:
        p = Project(name="Demo Panel Project A")
        db.add(p)
        db.flush()

        devices = [
            Device(project_id=p.id, tag="K1", description="Control Relay"),
            Device(project_id=p.id, tag="TS3", description="Test Switch"),
            Device(project_id=p.id, tag="SEL-751", description="Protective Relay"),
            Device(project_id=p.id, tag="CR14", description="Control Relay 14"),
        ]
        db.add_all(devices)
        db.flush()

        # Use any stable public image URL for demo. Replace later with your own.
        d = Drawing(
            project_id=p.id,
            title="Panel Layout (Demo)",
            image_url="https://upload.wikimedia.org/wikipedia/commons/thumb/3/3f/Placeholder_view_vector.svg/1280px-Placeholder_view_vector.svg.png",
        )
        db.add(d)
        db.flush()

        # Hotspots using 0-10000 scale (percent * 100)
        # (x,y,w,h) are rectangles on the image
        hotspots = [
            Hotspot(drawing_id=d.id, device_id=devices[0].id, x=1200, y=1800, w=1100, h=900, label="K1"),
            Hotspot(drawing_id=d.id, device_id=devices[1].id, x=4200, y=2200, w=1200, h=900, label="TS3"),
            Hotspot(drawing_id=d.id, device_id=devices[2].id, x=2500, y=5200, w=1600, h=1100, label="SEL-751"),
            Hotspot(drawing_id=d.id, device_id=devices[3].id, x=6500, y=3400, w=1200, h=900, label="CR14"),
        ]
        db.add_all(hotspots)

        # Optional: seed one issue to show highlighting immediately
        db.add(Issue(project_id=p.id, device_id=devices[2].id, drawing_id=d.id, severity="high", notes="Wiring mismatch suspected"))

        db.commit()
        print("Seeded demo.db successfully.")
    finally:
        db.close()

if __name__ == "__main__":
    seed()
