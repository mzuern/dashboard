import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { DashboardOverview } from "./components/DashboardOverview";

const API = "http://127.0.0.1:8000";

type ProjectSummary = {
  project_id: number;
  project_number: string;       // e.g. "4009"
  customer_name: string;        // e.g. "Acme Power"
  project_manager: string;      // e.g. "Dan"
  mfg_issue_count: number;      // total issues tagged manufacturing
  eng_issue_count: number;      // total issues tagged engineering
  open_issue_count: number;     // total open
  closed_issue_count: number;   // total closed
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

  const projectOptions = useMemo(() => {
    const unique = new Map<string, { label: string; value: string }>();
    for (const r of rows) {
      unique.set(r.project_number, { label: `${r.project_number} — ${r.customer_name}`, value: r.project_number });
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

      {/* Optional top cards (keep if you like) */}
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
                <Th label="Project #" onClick={() => toggleSort("project_number")} active={sortKey === "project_number"} dir={sortDir} />
                <Th label="Customer" onClick={() => toggleSort("customer_name")} active={sortKey === "customer_name"} dir={sortDir} />
                <Th label="PM" onClick={() => toggleSort("project_manager")} active={sortKey === "project_manager"} dir={sortDir} />
                <Th label="MFG Issues" onClick={() => toggleSort("mfg_issue_count")} active={sortKey === "mfg_issue_count"} dir={sortDir} />
                <Th label="ENG Issues" onClick={() => toggleSort("eng_issue_count")} active={sortKey === "eng_issue_count"} dir={sortDir} />
                <Th label="Open" onClick={() => toggleSort("open_issue_count")} active={sortKey === "open_issue_count"} dir={sortDir} />
                <Th label="Closed" onClick={() => toggleSort("closed_issue_count")} active={sortKey === "closed_issue_count"} dir={sortDir} />
                <Th label="Oldest Open (days)" onClick={() => toggleSort("oldest_open_days")} active={sortKey === "oldest_open_days"} dir={sortDir} />
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
                      {r.oldest_open_days == null ? (
                        <span style={{ opacity: 0.7 }}>—</span>
                      ) : (
                        r.oldest_open_days
                      )}
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

      <div style={{ marginTop: 12, fontSize: 12, opacity: 0.6 }}>
        API: {API} — Needs endpoint: <code>/dashboard/projects</code>
      </div>
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
