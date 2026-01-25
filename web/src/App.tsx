import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { DashboardOverview } from "./components/DashboardOverview";

const API = "http://127.0.0.1:8000";

type ProjectSummary = {
  project_id: number;
  project_number: string; // e.g. "4009"
  customer_name: string; // e.g. "Acme Power"
  project_manager: string; // e.g. "Dan"
  mfg_issue_count: number; // total issues tagged manufacturing
  eng_issue_count: number; // total issues tagged engineering
  open_issue_count: number; // total open
  closed_issue_count: number; // total closed
  oldest_open_days: number | null; // null if no open issues
};

type SortKey =
  | "project_number"
  | "customer_name"
  | "project_manager"
  | "mfg_issue_count"
  | "eng_issue_count"
  | "open_issue_count"
  | "closed_issue_count"
  | "oldest_open_days";

type SortDir = "asc" | "desc";

export default function App() {
  const [rows, setRows] = useState<ProjectSummary[]>([]);
  const [selectedProject, setSelectedProject] = useState<string>("ALL");

  // ---- page-1 header extractor (PDF ingest) ----
  const [pdfFiles, setPdfFiles] = useState<string[]>([]);
  const [selectedPdf, setSelectedPdf] = useState<string>("");
  const [headerBusy, setHeaderBusy] = useState(false);
  const [headerError, setHeaderError] = useState<string | null>(null);
  const [header, setHeader] = useState<any | null>(null);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [sortKey, setSortKey] = useState<SortKey>("project_number");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  // -------- load dashboard rows on start --------
  useEffect(() => {
    let mounted = true;
    setError(null);
    setBusy(true);

    axios
      .get<ProjectSummary[]>(`${API}/dashboard/projects`)
      .then((r) => {
        if (!mounted) return;
        setRows(r.data ?? []);
      })
      .catch((e) => {
        if (!mounted) return;
        setError(readAxiosError(e));
      })
      .finally(() => {
        if (!mounted) return;
        setBusy(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  // -------- load available sample PDFs (optional) --------
  useEffect(() => {
    axios
      .get<{ files: string[] }>(`${API}/ingest/files`)
      .then((r) => {
        const files = r.data?.files ?? [];
        setPdfFiles(files);
        if (!selectedPdf && files.length) setSelectedPdf(files[0]);
      })
      .catch(() => {
        // silent: ingest is optional
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function runHeaderExtract() {
  if (!selectedPdf) return;
  setHeaderError(null);
  setHeaderBusy(true);
  try {
    await axios.post(`${API}/ingest/scan`, null, { params: { file: selectedPdf } });

    // refresh dashboard from DB
    const r = await axios.get<ProjectSummary[]>(`${API}/dashboard/projects`);
    setRows(r.data ?? []);
  } catch (e) {
    setHeaderError(readAxiosError(e));
  } finally {
    setHeaderBusy(false);
  }
}


  async function uploadAndExtract(file: File) {
    setHeaderError(null);
    setHeaderBusy(true);
    setHeader(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const r = await axios.post(`${API}/ingest/page1/upload`, fd, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setHeader(r.data);
    } catch (e) {
      setHeaderError(readAxiosError(e));
    } finally {
      setHeaderBusy(false);
    }
  }

  const projectOptions = useMemo(() => {
    const unique = new Map<string, { label: string; value: string }>();
    for (const r of rows) {
      unique.set(r.project_number, {
        label: `${r.project_number} — ${r.customer_name}`,
        value: r.project_number,
      });
    }
    return [{ label: "ALL Projects", value: "ALL" }, ...Array.from(unique.values())];
  }, [rows]);

  const filteredRows = useMemo(() => {
    if (selectedProject === "ALL") return rows;
    return rows.filter((r) => r.project_number === selectedProject);
  }, [rows, selectedProject]);

  const sortedRows = useMemo(() => {
    const copy = [...filteredRows];

    copy.sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];

      // null handling (oldest_open_days can be null)
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;

      // number vs string
      if (typeof av === "number" && typeof bv === "number") {
        return sortDir === "asc" ? av - bv : bv - av;
      }

      const as = String(av).toLowerCase();
      const bs = String(bv).toLowerCase();
      if (as < bs) return sortDir === "asc" ? -1 : 1;
      if (as > bs) return sortDir === "asc" ? 1 : -1;
      return 0;
    });

    return copy;
  }, [filteredRows, sortKey, sortDir]);

  function toggleSort(key: SortKey) {
    if (key === sortKey) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  }

  return (
    <div style={{ fontFamily: "system-ui", padding: 16, maxWidth: 1400, margin: "0 auto" }}>
      <h1 style={{ margin: 0 }}>QC Metrics Dashboard</h1>
      <p style={{ marginTop: 6, opacity: 0.75 }}>
        Project summary: customer, PM, engineering vs manufacturing, and aging open items
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
          <select value={selectedProject} onChange={(e) => setSelectedProject(e.target.value)}>
            {projectOptions.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        </label>

        {busy ? <span style={{ opacity: 0.75 }}>Loading…</span> : null}
      </div>

      {/* ===== Two-column layout ===== */}
      <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
        {/* LEFT: Dashboard */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ marginBottom: 16 }}>
            <DashboardOverview />
          </div>

          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12 }}>
            <h2 style={{ marginTop: 0 }}>Projects</h2>

            {sortedRows.length === 0 ? (
              <div style={{ opacity: 0.7 }}>{busy ? "Loading…" : "No projects found."}</div>
            ) : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr>
                    <Th
                      label="Project #"
                      onClick={() => toggleSort("project_number")}
                      active={sortKey === "project_number"}
                      dir={sortDir}
                    />
                    <Th
                      label="Customer"
                      onClick={() => toggleSort("customer_name")}
                      active={sortKey === "customer_name"}
                      dir={sortDir}
                    />
                    <Th
                      label="PM"
                      onClick={() => toggleSort("project_manager")}
                      active={sortKey === "project_manager"}
                      dir={sortDir}
                    />
                    <Th
                      label="MFG Issues"
                      onClick={() => toggleSort("mfg_issue_count")}
                      active={sortKey === "mfg_issue_count"}
                      dir={sortDir}
                    />
                    <Th
                      label="ENG Issues"
                      onClick={() => toggleSort("eng_issue_count")}
                      active={sortKey === "eng_issue_count"}
                      dir={sortDir}
                    />
                    <Th
                      label="Open"
                      onClick={() => toggleSort("open_issue_count")}
                      active={sortKey === "open_issue_count"}
                      dir={sortDir}
                    />
                    <Th
                      label="Closed"
                      onClick={() => toggleSort("closed_issue_count")}
                      active={sortKey === "closed_issue_count"}
                      dir={sortDir}
                    />
                    <Th
                      label="Oldest Open (days)"
                      onClick={() => toggleSort("oldest_open_days")}
                      active={sortKey === "oldest_open_days"}
                      dir={sortDir}
                    />
                  </tr>
                </thead>

                <tbody>
                  {sortedRows.map((r) => {
                    const resolvedLabel = r.open_issue_count === 0 ? "Yes" : "No";
                    return (
                      <tr key={`${r.project_id}-${r.project_number}`} style={{ borderTop: "1px solid #eee" }}>
                        <td style={{ padding: "10px 6px", fontWeight: 700 }}>{r.project_number}</td>
                        <td style={{ padding: "10px 6px" }}>{r.customer_name}</td>
                        <td style={{ padding: "10px 6px" }}>{r.project_manager}</td>
                        <td style={{ padding: "10px 6px" }}>{r.mfg_issue_count}</td>
                        <td style={{ padding: "10px 6px" }}>{r.eng_issue_count}</td>
                        <td style={{ padding: "10px 6px" }}>{r.open_issue_count}</td>
                        <td style={{ padding: "10px 6px" }}>{r.closed_issue_count}</td>
                        <td style={{ padding: "10px 6px" }}>
                          {r.oldest_open_days == null ? <span style={{ opacity: 0.7 }}>—</span> : r.oldest_open_days}
                          <span style={{ marginLeft: 10, fontSize: 12, opacity: 0.6 }}>
                            Resolved: {resolvedLabel}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div style={{ marginTop: 12, fontSize: 12, opacity: 0.6 }}>API: {API}</div>
        </div>

        {/* RIGHT: PDF tools + viewer placeholder */}
        <div style={{ width: 420, flex: "0 0 420px" }}>
          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12, marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 8 }}>PDF Tools</h2>
            <div style={{ fontSize: 12, opacity: 0.7, marginBottom: 10 }}>
              Pick a sample PDF or upload one, then click Extract. (Uses <code>/ingest/page1</code>)
            </div>

            <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center" }}>
              <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
                Sample:
                <select value={selectedPdf} onChange={(e) => setSelectedPdf(e.target.value)} disabled={!pdfFiles.length}>
                  {pdfFiles.length ? (
                    pdfFiles.map((f) => (
                      <option key={f} value={f}>
                        {f}
                      </option>
                    ))
                  ) : (
                    <option value="">(no sample PDFs found)</option>
                  )}
                </select>
              </label>

              <button onClick={runHeaderExtract} disabled={!selectedPdf || headerBusy}>
                {headerBusy ? "Extracting…" : "Extract"}
              </button>

              <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
                Upload:
                <input
                  type="file"
                  accept="application/pdf"
                  disabled={headerBusy}
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) uploadAndExtract(f);
                    e.currentTarget.value = "";
                  }}
                />
              </label>
            </div>

            {headerError ? (
              <div
                style={{
                  marginTop: 10,
                  background: "#fee2e2",
                  border: "1px solid #fecaca",
                  color: "#7f1d1d",
                  padding: 10,
                  borderRadius: 10,
                  whiteSpace: "pre-wrap",
                }}
              >
                <b>Error:</b> {headerError}
              </div>
            ) : null}

            {header ? (
              <div style={{ marginTop: 10, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 10 }}>
                <Field label="Job #" value={header.job_number || header.project_number || "—"} />
                <Field label="Project Manager" value={header.project_manager || "—"} />
                <Field label="Date" value={header.date || "—"} />
                <Field label="Source" value={header.source_pdf || "—"} />

                <div style={{ gridColumn: "1 / -1" }}>
                  <details>
                    <summary style={{ cursor: "pointer" }}>Raw OCR text (debug)</summary>
                    <pre
                      style={{
                        marginTop: 8,
                        padding: 10,
                        background: "#f7f7f7",
                        borderRadius: 8,
                        overflowX: "auto",
                      }}
                    >
                      {String(header.raw_text ?? "")}
                    </pre>
                  </details>
                </div>
              </div>
            ) : null}
          </div>

          <div style={{ border: "1px dashed #bbb", borderRadius: 10, padding: 12, height: 520 }}>
            <h3 style={{ marginTop: 0 }}>PDF Viewer (Coming Soon)</h3>
            <div style={{ fontSize: 13, opacity: 0.7 }}>
              Reserved space for a viewer + highlight overlays.
              <br />
              We’ll drop the viewer here later without changing the layout again.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ border: "1px solid #e5e5e5", borderRadius: 10, padding: 10, minHeight: 64 }}>
      <div style={{ fontSize: 12, opacity: 0.7 }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4, wordBreak: "break-word" }}>{value}</div>
    </div>
  );
}

function Th({
  label,
  onClick,
  active,
  dir,
}: {
  label: string;
  onClick: () => void;
  active: boolean;
  dir: "asc" | "desc";
}) {
  return (
    <th
      onClick={onClick}
      style={{
        textAlign: "left",
        padding: "8px 6px",
        borderBottom: "1px solid #eee",
        cursor: "pointer",
        userSelect: "none",
        whiteSpace: "nowrap",
      }}
      title="Click to sort"
    >
      {label}{" "}
      {active ? (
        <span style={{ fontSize: 12, opacity: 0.7 }}>{dir === "asc" ? "▲" : "▼"}</span>
      ) : (
        <span style={{ fontSize: 12, opacity: 0.25 }}>↕</span>
      )}
    </th>
  );
}

function readAxiosError(e: any): string {
  const msg = e?.response?.data?.detail ?? e?.response?.data?.message ?? e?.message ?? "Unknown error";
  if (typeof msg === "string") return msg;
  try {
    return JSON.stringify(msg);
  } catch {
    return String(msg);
  }
}
