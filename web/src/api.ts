const API_BASE = "http://127.0.0.1:8000";

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function apiPost<T>(path: string, body: any): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function apiPatch<T>(path: string, body: any): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export type Page1Header = {
  project_number: string | null;
  project_name: string | null;
  project_manager: string | null;
  date: string | null;
  source_pdf?: string;
};

export async function listIngestFiles(): Promise<string[]> {
  const res = await fetch("http://127.0.0.1:8000/ingest/files");
  if (!res.ok) throw new Error("Failed to list ingest files");
  const data = await res.json();
  return data.files ?? [];
}

export async function extractPage1Header(file: string): Promise<Page1Header> {
  const url = new URL("http://127.0.0.1:8000/ingest/page1");
  url.searchParams.set("file", file);

  const res = await fetch(url.toString());
  if (!res.ok) throw new Error("Failed to extract page 1 header");
  return await res.json();
}
