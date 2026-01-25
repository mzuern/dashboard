import { useEffect, useMemo, useState } from "react";
import { extractPage1Header, listIngestFiles, type Page1Header } from "../api";

function cleanManager(s: string | null) {
  if (!s) return "";
  // Your OCR had “Bridget Becker uilding Wiring”
  // Display should only keep the name portion.
  // This keeps the first 2–3 words and drops the rest if it looks like junk.
  const parts = s.trim().split(/\s+/);
  return parts.slice(0, 2).join(" ");
}

function normalizeProjectName(projectName: string | null) {
  if (!projectName) return "";
  const upper = projectName.trim().toUpperCase();

  // For this run: force the exact display label you want
  if (upper === "HOG RUN") return "RWE/COMED Buffalo Solar — Hog Run";

  // Otherwise: just title-case it lightly
  return projectName
    .trim()
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function ProjectHeaderCard() {
  const [files, setFiles] = useState<string[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [header, setHeader] = useState<Page1Header | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string>("");

  useEffect(() => {
    (async () => {
      try {
        setErr("");
        const f = await listIngestFiles();
        setFiles(f);
        if (f.length && !selected) setSelected(f[0]);
      } catch (e: any) {
        setErr(e?.message ?? "Failed to load PDFs");
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const display = useMemo(() => {
    if (!header) return null;
    return {
      project_number: header.project_number ?? "",
      project_name: normalizeProjectName(header.project_name),
      project_manager: cleanManager(header.project_manager),
      date: header.date ?? "",
      source_pdf: header.source_pdf ?? selected,
    };
  }, [header, selected]);

  async function runExtract() {
    if (!selected) return;
    try {
      setLoading(true);
      setErr("");
      const result = await extractPage1Header(selected);
      setHeader(result);
    } catch (e: any) {
      setErr(e?.message ?? "Extraction failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ border: "1px solid rgba(255,255,255,0.12)", borderRadius: 12, padding: 16 }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>Project Header</div>
          <div style={{ opacity: 0.75, fontSize: 12 }}>Reads page 1 header fields only</div>
        </div>

        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <select
            value={selected}
            onChange={(e) => setSelected(e.target.value)}
            style={{ padding: "8px 10px", borderRadius: 8 }}
          >
            {files.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>

          <button
            onClick={runExtract}
            disabled={!selected || loading}
            style={{
              padding: "8px 12px",
              borderRadius: 8,
              cursor: loading ? "default" : "pointer",
            }}
          >
            {loading ? "Reading…" : "Extract"}
          </button>
        </div>
      </div>

      {err ? (
        <div style={{ marginTop: 12, color: "#ffb4b4" }}>{err}</div>
      ) : null}

      {!display ? (
        <div style={{ marginTop: 16, opacity: 0.7 }}>Select a PDF and click Extract.</div>
      ) : (
        <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <Field label="Project No" value={display.project_number} />
          <Field label="Date" value={display.date} />
          <div style={{ gridColumn: "1 / -1" }}>
            <Field label="Project Name" value={display.project_name} />
          </div>
          <div style={{ gridColumn: "1 / -1" }}>
            <Field label="Project Manager" value={display.project_manager} />
          </div>
          <div style={{ gridColumn: "1 / -1", opacity: 0.6, fontSize: 12 }}>
            Source PDF: {display.source_pdf}
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ padding: 12, borderRadius: 10, background: "rgba(255,255,255,0.06)" }}>
      <div style={{ fontSize: 12, opacity: 0.75 }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>{value || "—"}</div>
    </div>
  );
}
