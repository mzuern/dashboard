import { useEffect, useState } from "react";
import axios from "axios";

const API = "http://127.0.0.1:8000";

type LatestProject = {
  project_number: string;
  customer_name: string;
  project_manager: string;
  date: string | null;
  eng_issue_count: number;
  mfg_issue_count: number;
  open_issue_count: number;
  oldest_open_days: number | null;
  last_scanned_at: string | null;
};

export default function App() {
  const [pdfFiles, setPdfFiles] = useState<string[]>([]);
  const [selectedPdf, setSelectedPdf] = useState<string>("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadKey, setUploadKey] = useState(0);
  const [scanBusy, setScanBusy] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);

  const [latest, setLatest] = useState<LatestProject | null>(null);
  const [latestBusy, setLatestBusy] = useState(false);
  const [latestError, setLatestError] = useState<string | null>(null);

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

  useEffect(() => {
    void fetchLatest(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function fetchLatest(silent: boolean) {
    if (!silent) setLatestError(null);
    setLatestBusy(true);
    try {
      const r = await axios.get<LatestProject>(`${API}/dashboard/latest`);
      setLatest(r.data ?? null);
    } catch (e: any) {
      if (e?.response?.status === 404) {
        setLatest(null);
        setLatestError(null);
      } else if (!silent) {
        setLatestError(readAxiosError(e));
      }
    } finally {
      setLatestBusy(false);
    }
  }

  function clearUpload() {
    setUploadFile(null);
    setUploadKey((k) => k + 1);
  }

  async function runScan() {
    if (scanBusy) return;
    if (!uploadFile && !selectedPdf) return;
    setScanError(null);
    setScanBusy(true);
    try {
      if (uploadFile) {
        const fd = new FormData();
        fd.append("file", uploadFile);
        await axios.post(`${API}/ingest/scan/upload`, fd, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      } else {
        await axios.post(`${API}/ingest/scan`, null, { params: { file: selectedPdf } });
      }
      await fetchLatest(true);
      clearUpload();
    } catch (e) {
      setScanError(readAxiosError(e));
    } finally {
      setScanBusy(false);
    }
  }

  return (
    <div style={{ fontFamily: "system-ui", padding: 16, maxWidth: 1400, margin: "0 auto" }}>
      <h1 style={{ margin: 0 }}>QC Metrics Dashboard</h1>
      <p style={{ marginTop: 6, opacity: 0.75 }}>Single-job QC view with scan + save workflow</p>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 16, marginBottom: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
              <div>
                <div style={{ fontSize: 18, fontWeight: 700 }}>Job Header</div>
                <div style={{ fontSize: 12, opacity: 0.7 }}>Latest scanned job</div>
              </div>
              {latestBusy ? <div style={{ fontSize: 12, opacity: 0.6 }}>Refreshing...</div> : null}
            </div>

            {latestError ? (
              <div
                style={{
                  marginTop: 12,
                  background: "#fee2e2",
                  border: "1px solid #fecaca",
                  color: "#7f1d1d",
                  padding: 10,
                  borderRadius: 10,
                  whiteSpace: "pre-wrap",
                }}
              >
                <b>Error:</b> {latestError}
              </div>
            ) : null}

            {!latest ? (
              <div style={{ marginTop: 12, opacity: 0.7 }}>No scans yet. Run Scan & Save to load a job.</div>
            ) : (
              <div
                style={{
                  marginTop: 12,
                  display: "grid",
                  gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                  gap: 12,
                }}
              >
                <Field label="Project #" value={latest.project_number || "-"} />
                <Field label="Customer" value={latest.customer_name || "-"} />
                <Field label="Project Manager" value={latest.project_manager || "-"} />
                <Field label="Date" value={latest.date || "-"} />
                <Field label="Last Scanned" value={formatTimestamp(latest.last_scanned_at)} />
              </div>
            )}
          </div>

          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 16 }}>
            <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 12 }}>Issue Summary</div>
            {!latest ? (
              <div style={{ opacity: 0.7 }}>No issue summary yet.</div>
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 12 }}>
                <Metric label="ENG Issues" value={String(latest.eng_issue_count)} />
                <Metric label="MFG Issues" value={String(latest.mfg_issue_count)} />
                <Metric label="Open Issues" value={String(latest.open_issue_count)} />
                <Metric
                  label="Oldest Open (days)"
                  value={latest.oldest_open_days == null ? "-" : String(latest.oldest_open_days)}
                />
              </div>
            )}
          </div>

          <div style={{ marginTop: 12, fontSize: 12, opacity: 0.6 }}>API: {API}</div>
        </div>

        <div style={{ width: 420, flex: "0 0 420px" }}>
          <div style={{ border: "1px solid #ddd", borderRadius: 10, padding: 12, marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 8 }}>PDF Tools</h2>
            <div style={{ fontSize: 12, opacity: 0.7, marginBottom: 10 }}>
              Pick a sample PDF or upload one, then click Scan & Save.
            </div>

            <div style={{ display: "grid", gap: 12 }}>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center" }}>
                <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  Sample:
                  <select
                    value={selectedPdf}
                    onChange={(e) => setSelectedPdf(e.target.value)}
                    disabled={!pdfFiles.length || Boolean(uploadFile)}
                  >
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

                <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  Upload:
                  <input
                    key={uploadKey}
                    type="file"
                    accept="application/pdf"
                    disabled={scanBusy}
                    onChange={(e) => {
                      const f = e.target.files?.[0] ?? null;
                      setUploadFile(f);
                    }}
                  />
                </label>

                {uploadFile ? (
                  <div style={{ fontSize: 12, opacity: 0.75 }}>Selected: {uploadFile.name}</div>
                ) : (
                  <div style={{ fontSize: 12, opacity: 0.6 }}>No upload selected</div>
                )}

                {uploadFile ? (
                  <button onClick={clearUpload} disabled={scanBusy}>
                    Clear Upload
                  </button>
                ) : null}
              </div>

              <button onClick={runScan} disabled={scanBusy || (!uploadFile && !selectedPdf)}>
                {scanBusy ? "Scanning..." : "Scan & Save"}
              </button>
            </div>

            {scanError ? (
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
                <b>Error:</b> {scanError}
              </div>
            ) : null}
          </div>

          <div style={{ border: "1px dashed #bbb", borderRadius: 10, padding: 12, height: 520 }}>
            <h3 style={{ marginTop: 0 }}>PDF Viewer (Coming Soon)</h3>
            <div style={{ fontSize: 13, opacity: 0.7 }}>
              Reserved space for a viewer + highlight overlays.
              <br />
              We'll drop the viewer here later without changing the layout again.
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        background: "#f7f7f7",
        borderRadius: 10,
        padding: 14,
        border: "1px solid #e5e5e5",
      }}
    >
      <div style={{ fontSize: 12, opacity: 0.7 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, marginTop: 6 }}>{value}</div>
    </div>
  );
}

function formatTimestamp(ts: string | null | undefined) {
  if (!ts) return "-";
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
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
