import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { DashboardOverview } from "./components/DashboardOverview";

const API = "http://127.0.0.1:8000";

type Project = { id: number; name: string };

type Hotspot = {
  id: number;
  drawing_id: number;
  device_id: number;
  x: number;
  y: number;
  w: number;
  h: number;
  label?: string | null;
};

type Drawing = {
  id: number;
  project_id: number;
  title: string;
  image_url: string;
  hotspots: Hotspot[];
};

type Issue = {
  id: number;
  project_id: number;
  device_id: number;
  drawing_id?: number | null;
  severity: string;
  status: string;
  notes?: string | null;
};

type TestLine = {
  device_id: number;
  tag: string;
  description?: string | null;
  has_open_issue: boolean;
};

export default function App() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectId, setProjectId] = useState<number | null>(null);

  const [drawings, setDrawings] = useState<Drawing[]>([]);
  const [activeDrawing, setActiveDrawing] = useState<Drawing | null>(null);

  const [issues, setIssues] = useState<Issue[]>([]);
  const [testLines, setTestLines] = useState<TestLine[]>([]);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // -------- load projects on start --------
  useEffect(() => {
    let mounted = true;
    setError(null);

    axios
      .get<Project[]>(`${API}/projects`)
      .then((r) => {
        if (!mounted) return;
        setProjects(r.data);
        if (r.data.length) setProjectId(r.data[0].id);
      })
      .catch((e) => {
        if (!mounted) return;
        setError(readAxiosError(e));
      });

    return () => {
      mounted = false;
    };
  }, []);

  // -------- load drawings + issues + testsheet when project changes --------
  useEffect(() => {
    if (!projectId) return;

    let mounted = true;
    setError(null);

    (async () => {
      try {
        const d = await axios.get<Drawing[]>(`${API}/projects/${projectId}/drawings`);
        if (!mounted) return;

        setDrawings(d.data);
        setActiveDrawing(d.data[0] ?? null);

        await refreshIssuesAndSheet(projectId, mounted);
      } catch (e) {
        if (!mounted) return;
        setError(readAxiosError(e));
      }
    })();

    return () => {
      mounted = false;
    };
  }, [projectId]);

  async function refreshIssuesAndSheet(pid: number, mounted: boolean = true) {
    const [iss, sheet] = await Promise.all([
      axios.get<Issue[]>(`${API}/projects/${pid}/issues`),
      axios.get<{ project_id: number; lines: TestLine[] }>(`${API}/projects/${pid}/testsheets`),
    ]);

    if (!mounted) return;

    setIssues(iss.data);
    setTestLines(sheet.data.lines);
  }

  const issueDeviceIds = useMemo(() => {
    return new Set(issues.filter((i) => i.status === "open").map((i) => i.device_id));
  }, [issues]);

  async function onHotspotClick(h: Hotspot) {
    if (!projectId || !activeDrawing) return;

    setBusy(true);
    setError(null);
    try {
      await axios.post(`${API}/issues`, {
        project_id: projectId,
        device_id: h.device_id,
        drawing_id: activeDrawing.id,
        severity: "medium",
        notes: `Flagged from drawing hotspot ${h.label ?? ""}`.trim(),
      });

      await refreshIssuesAndSheet(projectId);
    } catch (e) {
      setError(readAxiosError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ fontFamily: "system-ui", padding: 16, maxWidth: 1400, margin: "0 auto" }}>
      <h1 style={{ margin: 0 }}>Dashboard Demo</h1>
      <p style={{ marginTop: 6, opacity: 0.75 }}>
        Click a hotspot → creates Issue → test sheet highlights device
      </p>

      {error ? (
        <div
          style={{
            background: "#fee2e2",
            border: "1px solid #fecaca",
            color: "#7f1d1d",
            padding: 12,
            borderRadius: 10,
            marginBottom: 12,
            whiteSpace: "pre-wrap",
          }}
        >
          <b>Error:</b> {error}
        </div>
      ) : null}

      <div style={{ display: "flex", gap: 16, alignItems: "center", marginBottom: 12 }}>
        <label>
          Project:&nbsp;
          <select
            value={projectId ?? ""}
            onChange={(e) => setProjectId(Number(e.target.value))}
            disabled={projects.length === 0}
          >
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          Drawing:&nbsp;
          <select
            value={activeDrawing?.id ?? ""}
            onChange={(e) => {
              const id = Number(e.target.value);
              setActiveDrawing(drawings.find((d) => d.id === id) ?? null);
            }}
            disabled={drawings.length === 0}
          >
            {drawings.map((d) => (
              <option key={d.id} value={d.id}>
                {d.title}
              </option>
            ))}
          </select>
        </label>

        {busy ? <span style={{ opacity: 0.75 }}>Working…</span> : null}
      </div>

      {/* Top Overview */}
      <div style={{ marginBottom: 16 }}>
        <DashboardOverview />
      </div>

      {/* Main Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
        {/* Drawing Viewer */}
        <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12 }}>
          <h2 style={{ marginTop: 0 }}>Drawing Map</h2>
          {!activeDrawing ? (
            <div style={{ opacity: 0.7 }}>No drawing loaded</div>
          ) : (
            <DrawingViewer
              drawing={activeDrawing}
              issueDeviceIds={issueDeviceIds}
              onHotspotClick={onHotspotClick}
            />
          )}
        </div>

        {/* Right Column */}
        <div style={{ display: "grid", gap: 16 }}>
          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12 }}>
            <h2 style={{ marginTop: 0 }}>Open Issues</h2>

            {issues.length === 0 ? (
              <div style={{ opacity: 0.7 }}>None</div>
            ) : (
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {issues.map((i) => (
                  <li key={i.id}>
                    <b>Device #{i.device_id}</b> — {i.severity} — {i.status}
                    {i.notes ? <div style={{ opacity: 0.8 }}>{i.notes}</div> : null}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12 }}>
            <h2 style={{ marginTop: 0 }}>Test Sheet (Demo)</h2>
            <div style={{ fontSize: 13, opacity: 0.7, marginBottom: 8 }}>
              Highlighted rows mean “has open issue”
            </div>

            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={{ textAlign: "left", borderBottom: "1px solid #eee", paddingBottom: 6 }}>Tag</th>
                  <th style={{ textAlign: "left", borderBottom: "1px solid #eee", paddingBottom: 6 }}>
                    Description
                  </th>
                  <th style={{ textAlign: "left", borderBottom: "1px solid #eee", paddingBottom: 6 }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {testLines.map((line) => (
                  <tr
                    key={line.device_id}
                    style={{ background: line.has_open_issue ? "#fff3cd" : "transparent" }}
                  >
                    <td style={{ padding: "6px 0" }}>
                      <b>{line.tag}</b>
                    </td>
                    <td style={{ padding: "6px 0", opacity: 0.8 }}>{line.description ?? ""}</td>
                    <td style={{ padding: "6px 0" }}>{line.has_open_issue ? "Attention" : "OK"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 12, opacity: 0.6 }}>
        API: {API} — Endpoints: /projects, /projects/:id/drawings, /issues, /projects/:id/issues, /projects/:id/testsheets
      </div>
    </div>
  );
}

function DrawingViewer({
  drawing,
  issueDeviceIds,
  onHotspotClick,
}: {
  drawing: Drawing;
  issueDeviceIds: Set<number>;
  onHotspotClick: (h: Hotspot) => void;
}) {
  return (
    <div style={{ position: "relative" }}>
      <img
        src={drawing.image_url}
        alt={drawing.title}
        style={{ width: "100%", borderRadius: 8, display: "block" }}
      />

      {drawing.hotspots.map((h) => {
        const left = `${h.x / 100}%`;
        const top = `${h.y / 100}%`;
        const width = `${h.w / 100}%`;
        const height = `${h.h / 100}%`;

        const hasIssue = issueDeviceIds.has(h.device_id);

        return (
          <button
            key={h.id}
            onClick={() => onHotspotClick(h)}
            title={`Device ${h.label ?? ""} (id ${h.device_id})`}
            style={{
              position: "absolute",
              left,
              top,
              width,
              height,
              border: hasIssue ? "2px solid #d9480f" : "2px solid #2563eb",
              background: hasIssue ? "rgba(217,72,15,0.15)" : "rgba(37,99,235,0.15)",
              borderRadius: 8,
              cursor: "pointer",
              padding: 0,
            }}
          >
            <span
              style={{
                position: "absolute",
                left: 6,
                top: 6,
                fontSize: 12,
                background: "rgba(0,0,0,0.7)",
                color: "white",
                padding: "2px 6px",
                borderRadius: 999,
              }}
            >
              {h.label ?? `#${h.device_id}`}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function readAxiosError(e: any): string {
  // Axios error shape
  const msg =
    e?.response?.data?.detail ??
    e?.response?.data?.message ??
    e?.message ??
    "Unknown error";
  if (typeof msg === "string") return msg;
  try {
    return JSON.stringify(msg);
  } catch {
    return String(msg);
  }
}
