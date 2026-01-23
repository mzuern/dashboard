import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost } from "../api";

type Device = { id: number; project_id: number; tag: string; description?: string | null };

type Issue = {
  id: number;
  project_id: number;
  device_id: number | null;
  drawing_id?: number | null;
  severity: string;
  status: string;
  notes?: string | null;
};

export default function DevicePanel({
  deviceId,
  onClose,
  defaultProjectId,
  defaultDrawingId,
}: {
  deviceId: number | null;
  onClose: () => void;
  defaultProjectId: number | null;
  defaultDrawingId: number | null;
}) {
  const [device, setDevice] = useState<Device | null>(null);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [selectedIssueId, setSelectedIssueId] = useState<number | "new">("new");
  const [notes, setNotes] = useState("");
  const [severity, setSeverity] = useState("medium");
  const [error, setError] = useState<string | null>(null);

  async function refresh(did: number) {
    const d = await apiGet<Device>(`/devices/${did}`);
    const iss = await apiGet<Issue[]>(`/devices/${did}/issues`);
    setDevice(d);
    setIssues(iss);
  }

  useEffect(() => {
    setError(null);
    setSelectedIssueId("new");
    setNotes("");
    setSeverity("medium");

    if (!deviceId) {
      setDevice(null);
      setIssues([]);
      return;
    }

    refresh(deviceId).catch((e) => setError(String(e)));
  }, [deviceId]);

  const selectedIssue = useMemo(() => {
    if (selectedIssueId === "new") return null;
    return issues.find((i) => i.id === selectedIssueId) ?? null;
  }, [issues, selectedIssueId]);

  useEffect(() => {
    if (!selectedIssue) {
      setNotes("");
      setSeverity("medium");
      return;
    }
    setNotes(selectedIssue.notes ?? "");
    setSeverity(selectedIssue.severity ?? "medium");
  }, [selectedIssue]);

  if (!deviceId) return null;

  async function createIssue() {
    if (!device) return;
    if (!defaultProjectId) throw new Error("No project selected");

    const created = await apiPost<Issue>("/issues", {
      project_id: defaultProjectId,
      device_id: device.id,
      drawing_id: defaultDrawingId,
      severity,
      notes: notes?.trim() ? notes.trim() : null,
    });

    await refresh(device.id);
    setSelectedIssueId(created.id);
  }

  async function closeIssue(issueId: number) {
    await apiPatch<Issue>(`/issues/${issueId}`, { status: "closed" });
    if (device) await refresh(device.id);
  }

  async function reopenIssue(issueId: number) {
    await apiPatch<Issue>(`/issues/${issueId}`, { status: "open" });
    if (device) await refresh(device.id);
  }

  return (
    <div style={{
      position: "fixed", right: 12, top: 12, bottom: 12,
      width: 360, background: "#111827", color: "#e5e7eb",
      border: "1px solid #1f2937", borderRadius: 12, padding: 12, overflow: "auto",
      boxShadow: "0 10px 30px rgba(0,0,0,0.35)"
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>
            {device?.tag ?? `Device ${deviceId}`}
          </div>
          <div style={{ fontSize: 12, color: "#9ca3af" }}>{device?.description ?? ""}</div>
        </div>
        <button onClick={onClose} style={{ background: "#1f2937", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 8, padding: "6px 10px" }}>
          Close
        </button>
      </div>

      {error ? <div style={{ marginTop: 10, color: "#fca5a5", fontSize: 12 }}>{error}</div> : null}

      <div style={{ marginTop: 12 }}>
        <div style={{ fontSize: 12, color: "#9ca3af", marginBottom: 6 }}>Issues for this device</div>
        <select
          value={selectedIssueId}
          onChange={(e) => setSelectedIssueId(e.target.value === "new" ? "new" : Number(e.target.value))}
          style={{ width: "100%", background: "#0b1220", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 8, padding: 8 }}
        >
          <option value="new">+ New issue…</option>
          {issues.map((i) => (
            <option key={i.id} value={i.id}>
              #{i.id} — {i.status.toUpperCase()} — {i.severity}
            </option>
          ))}
        </select>
      </div>

      <div style={{ marginTop: 12 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <div style={{ fontSize: 12, color: "#9ca3af" }}>Severity</div>
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
            style={{ flex: 1, background: "#0b1220", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 8, padding: 8 }}
          >
            <option value="low">low</option>
            <option value="medium">medium</option>
            <option value="high">high</option>
          </select>
        </div>

        <div style={{ marginTop: 10 }}>
          <div style={{ fontSize: 12, color: "#9ca3af", marginBottom: 6 }}>Notes</div>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={6}
            style={{ width: "100%", background: "#0b1220", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 8, padding: 8 }}
            placeholder="Describe the issue…"
          />
        </div>

        {selectedIssueId === "new" ? (
          <button
            onClick={() => createIssue().catch((e) => setError(String(e)))}
            style={{ marginTop: 10, width: "100%", background: "#2563eb", color: "white", border: "none", borderRadius: 10, padding: 10, fontWeight: 700 }}
          >
            Create Issue
          </button>
        ) : selectedIssue ? (
          <div style={{ marginTop: 10, display: "flex", gap: 8 }}>
            {selectedIssue.status === "open" ? (
              <button
                onClick={() => closeIssue(selectedIssue.id).catch((e) => setError(String(e)))}
                style={{ flex: 1, background: "#111827", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 10, padding: 10 }}
              >
                Close Issue
              </button>
            ) : (
              <button
                onClick={() => reopenIssue(selectedIssue.id).catch((e) => setError(String(e)))}
                style={{ flex: 1, background: "#111827", color: "#e5e7eb", border: "1px solid #374151", borderRadius: 10, padding: 10 }}
              >
                Reopen Issue
              </button>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}
