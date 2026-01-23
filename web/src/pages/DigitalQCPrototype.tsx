import React, { useEffect, useMemo, useRef, useState } from "react";

/**
 * Digital QC Prototype (React port of Index9.html)
 * - 4 "pages" inside a swipeable strip:
 *   1) Dashboard Summary
 *   2) QC-18 Housewire
 *   3) QC-18 Integration
 *   4) EE Test Sheet
 *
 * - Gauges auto-update based on checkbox completion
 * - Flags auto-update if any checkbox with data-flag=true is checked
 * - Job + Employee filters update labels everywhere
 * - Time tracking per job+sheet+employee with Start/Stop
 *
 * NOTE: This is intentionally "demo-local state".
 * Next step is to persist to your FastAPI backend (easy).
 */

type Job = { id: string; label: string };
type Employee = { id: string; label: string; name: string };

type SheetId = "summary" | "housewire" | "integration" | "ee";

type TimeLog = Record<
  string, // jobId
  Partial<Record<Exclude<SheetId, "summary">, Partial<Record<string, number>>>>
>;

const sheetNames: Record<SheetId, string> = {
  summary: "Dashboard",
  housewire: "Housewire",
  integration: "Integration",
  ee: "EE Test",
};

const sheetLabels: Record<Exclude<SheetId, "summary">, string> = {
  housewire: "QC-18 Housewire",
  integration: "QC-18 Integration",
  ee: "EE Test",
};

const jobs: Job[] = [
  { id: "61148", label: "61148 – Invenergy Lazboddie Wind" },
  { id: "61210", label: "61210 – Example Solar Farm" },
  { id: "61325", label: "61325 – Data Center Upgrade" },
];

const employees: Employee[] = [
  { id: "GW", label: "GW – Gordon Walker", name: "Gordon Walker" },
  { id: "MZ", label: "MZ – Matt Zuern", name: "Matt Zuern" },
  { id: "MM", label: "MM – Mike Moser", name: "Mike Moser" },
  { id: "TG", label: "TG – Tom Golfis", name: "Tom Golfis" },
];

function clamp(n: number, a: number, b: number) {
  return Math.max(a, Math.min(b, n));
}

function formatTime(sec: number) {
  const total = Math.max(0, Math.floor(sec));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function percent(checked: number, total: number) {
  if (!total) return 0;
  return Math.round((checked / total) * 100);
}

type ChecklistItem = {
  id: string;
  label: string;
  isFlag?: boolean; // if checked => sheet flagged
};

const housewireItems: ChecklistItem[] = [
  { id: "hw-1", label: "Issue board reviewed for missing materials / open items." },
  { id: "hw-2", label: "QC-18 print checked against current revision." },
  { id: "hw-3", label: "All conductors landed per print (color / size verified)." },
  { id: "hw-4", label: "Boxes, wireways, receptacles, light switches properly wired." },
  { id: "hw-5", label: "Grounds verified and bonded per drawing." },
  { id: "hw-6", label: "Nameplates installed and legible." },
  {
    id: "hw-flag",
    label: "Issue found that requires write-up on comments sheet.",
    isFlag: true,
  },
];

const integrationItems: ChecklistItem[] = [
  { id: "in-1", label: "All required panels, racks and devices installed." },
  { id: "in-2", label: "Shipping materials removed, moving parts free of obstruction." },
  { id: "in-3", label: "AC voltage applied per print, all loads operate correctly." },
  { id: "in-4", label: "Relays / coils verified for correct operation and labeling." },
  { id: "in-5", label: "All interlocks / alarms verified per functional test requirements." },
  {
    id: "in-flag",
    label: "Integration issue found that requires write-up / PM notification.",
    isFlag: true,
  },
];

const eeItems: ChecklistItem[] = [
  { id: "ee-1", label: "All covers in place or barriers installed per procedure." },
  { id: "ee-2", label: "Test equipment calibrated and recorded on sheet." },
  { id: "ee-3", label: "Dielectric / hipot testing completed and results acceptable." },
  { id: "ee-4", label: "Polarity and phasing verified per drawing and nameplates." },
  { id: "ee-5", label: "Control circuits function tested (start/stop, alarms, trips)." },
  { id: "ee-flag", label: "Test failure / abnormal condition observed.", isFlag: true },
];

const dark = {
  bg: "#0f1115",
  panel: "#131621",
  border: "#1d212b",
  textMain: "#eef1f6",
  textSub: "#9ca7ba",
  accent: "#8bd3ff",
  flag: "#ff7575",
};

function GaugeCard({
  title,
  pct,
  flagged,
  onClick,
}: {
  title: string;
  pct: number;
  flagged: boolean;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        background: dark.panel,
        border: `1px solid ${dark.border}`,
        borderRadius: 12,
        padding: "8px 10px",
        fontSize: 12,
        position: "relative",
        cursor: onClick ? "pointer" : "default",
        userSelect: "none",
      }}
    >
      <div style={{ color: dark.textSub, marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 6 }}>{pct}%</div>
      <div
        style={{
          width: "100%",
          height: 6,
          borderRadius: 999,
          background: "#0b0d13",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            height: "100%",
            width: `${clamp(pct, 0, 100)}%`,
            background: dark.accent,
            transition: "width 0.2s ease",
          }}
        />
      </div>
      <div
        style={{
          position: "absolute",
          top: 6,
          right: 8,
          fontSize: 11,
          color: dark.flag,
          fontWeight: 700,
        }}
      >
        {flagged ? "⚠ Flag" : ""}
      </div>
    </div>
  );
}

function Checklist({
  items,
  checkedMap,
  onToggle,
}: {
  items: ChecklistItem[];
  checkedMap: Record<string, boolean>;
  onToggle: (id: string, next: boolean) => void;
}) {
  return (
    <div style={{ display: "grid", gap: 6 }}>
      {items.map((it) => (
        <label
          key={it.id}
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: 8,
            lineHeight: 1.2,
            fontSize: 13,
          }}
        >
          <input
            type="checkbox"
            checked={!!checkedMap[it.id]}
            onChange={(e) => onToggle(it.id, e.target.checked)}
            style={{ marginTop: 2 }}
          />
          <span>{it.label}</span>
        </label>
      ))}
    </div>
  );
}

export default function DigitalQCPrototype() {
  const pages: SheetId[] = ["summary", "housewire", "integration", "ee"];

  const [currentIndex, setCurrentIndex] = useState(0);

  const [jobId, setJobId] = useState(jobs[0].id);
  const [employeeId, setEmployeeId] = useState(employees[0].id);

  const selectedJobLabel = useMemo(
    () => jobs.find((j) => j.id === jobId)?.label ?? jobId,
    [jobId]
  );
  const selectedEmployeeLabel = useMemo(
    () => employees.find((e) => e.id === employeeId)?.label ?? employeeId,
    [employeeId]
  );

  // Checkbox state per sheet
  const [checks, setChecks] = useState<Record<Exclude<SheetId, "summary">, Record<string, boolean>>>({
    housewire: {},
    integration: {},
    ee: {},
  });

  // Time tracking
  const [timeLog, setTimeLog] = useState<TimeLog>({});
  const activeTimerRef = useRef<{
    jobId: string;
    sheetId: Exclude<SheetId, "summary">;
    employeeId: string;
    startMs: number;
  } | null>(null);
  const [tick, setTick] = useState(0); // forces UI updates while timer runs

  useEffect(() => {
    const iv = setInterval(() => {
      if (activeTimerRef.current) setTick((t) => t + 1);
    }, 1000);
    return () => clearInterval(iv);
  }, []);

  function stopTimer() {
    const active = activeTimerRef.current;
    if (!active) return;

    const deltaSec = (Date.now() - active.startMs) / 1000;

    setTimeLog((prev) => {
      const next: TimeLog = { ...prev };
      if (!next[active.jobId]) next[active.jobId] = {};
      if (!next[active.jobId][active.sheetId]) next[active.jobId][active.sheetId] = {};
      const existing = next[active.jobId][active.sheetId]![active.employeeId] ?? 0;
      next[active.jobId][active.sheetId]![active.employeeId] = existing + deltaSec;
      return next;
    });

    activeTimerRef.current = null;
  }

  function startTimer(sheetId: Exclude<SheetId, "summary">) {
    // if already running, stop first
    if (activeTimerRef.current) stopTimer();

    activeTimerRef.current = {
      jobId,
      sheetId,
      employeeId,
      startMs: Date.now(),
    };
    setTick((t) => t + 1);
  }

  // Stop timer when switching job/employee (matching your HTML behavior)
  useEffect(() => {
    if (activeTimerRef.current) stopTimer();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId, employeeId]);

  function getStoredSeconds(job: string, sheet: Exclude<SheetId, "summary">, emp: string) {
    const base = timeLog[job]?.[sheet]?.[emp] ?? 0;
    const active = activeTimerRef.current;
    if (active && active.jobId === job && active.sheetId === sheet && active.employeeId === emp) {
      const delta = (Date.now() - active.startMs) / 1000;
      return base + delta;
    }
    return base;
  }

  const housewireStats = useMemo(() => {
    const map = checks.housewire;
    const total = housewireItems.length;
    const checked = housewireItems.filter((i) => map[i.id]).length;
    const flagged = housewireItems.some((i) => i.isFlag && map[i.id]);
    return { pct: percent(checked, total), flagged };
  }, [checks.housewire]);

  const integrationStats = useMemo(() => {
    const map = checks.integration;
    const total = integrationItems.length;
    const checked = integrationItems.filter((i) => map[i.id]).length;
    const flagged = integrationItems.some((i) => i.isFlag && map[i.id]);
    return { pct: percent(checked, total), flagged };
  }, [checks.integration]);

  const eeStats = useMemo(() => {
    const map = checks.ee;
    const total = eeItems.length;
    const checked = eeItems.filter((i) => map[i.id]).length;
    const flagged = eeItems.some((i) => i.isFlag && map[i.id]);
    return { pct: percent(checked, total), flagged };
  }, [checks.ee]);

  const anyFlag = housewireStats.flagged || integrationStats.flagged || eeStats.flagged;

  const timeSummaryRows = useMemo(() => {
    const jobData = timeLog[jobId] || {};
    const rows: Array<{ sheetId: string; emp: string; minutes: number }> = [];

    Object.keys(jobData).forEach((sheet) => {
      const sheetData = jobData[sheet as Exclude<SheetId, "summary">] || {};
      Object.keys(sheetData).forEach((emp) => {
        const sec = sheetData[emp] || 0;
        if (!sec) return;
        rows.push({ sheetId: sheet, emp, minutes: Math.round(sec / 60) });
      });
    });

    // If timer is active, include “live” minutes for current selection even if base is 0
    const active = activeTimerRef.current;
    if (active && active.jobId === jobId) {
      const liveSec = getStoredSeconds(jobId, active.sheetId, active.employeeId);
      const already = rows.find((r) => r.sheetId === active.sheetId && r.emp === active.employeeId);
      const liveMin = Math.round(liveSec / 60);
      if (already) already.minutes = liveMin;
      else if (liveSec > 0) rows.push({ sheetId: active.sheetId, emp: active.employeeId, minutes: liveMin });
    }

    // stable ordering: sheet then emp
    rows.sort((a, b) => (a.sheetId + a.emp).localeCompare(b.sheetId + b.emp));
    return rows;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeLog, jobId, tick]);

  // Swipe handling
  const touchStartXRef = useRef<number | null>(null);

  function goToIndex(idx: number) {
    setCurrentIndex(clamp(idx, 0, pages.length - 1));
  }

  function onTouchStart(e: React.TouchEvent) {
    touchStartXRef.current = e.touches[0].clientX;
  }

  function onTouchEnd(e: React.TouchEvent) {
    const startX = touchStartXRef.current;
    if (startX == null) return;
    const dx = e.changedTouches[0].clientX - startX;
    const threshold = 40;
    if (dx < -threshold) goToIndex(currentIndex + 1);
    else if (dx > threshold) goToIndex(currentIndex - 1);
    touchStartXRef.current = null;
  }

  function toggle(sheet: Exclude<SheetId, "summary">, id: string, next: boolean) {
    setChecks((prev) => ({
      ...prev,
      [sheet]: { ...prev[sheet], [id]: next },
    }));
  }

  const wrapStyle: React.CSSProperties = {
    background: dark.bg,
    color: dark.textMain,
    minHeight: "100vh",
    padding: 12,
    fontFamily:
      'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  };

  const cardStyle: React.CSSProperties = {
    background: dark.panel,
    border: `1px solid ${dark.border}`,
    borderRadius: 12,
    overflow: "hidden",
    position: "relative",
  };

  const stripStyle: React.CSSProperties = {
    display: "flex",
    transform: `translateX(-${currentIndex * 100}%)`,
    transition: "transform 0.25s ease",
    touchAction: "pan-y",
  };

  const sheetStyle: React.CSSProperties = {
    flex: "0 0 100%",
    padding: 12,
    fontSize: 12,
  };

  const sectionTitleStyle: React.CSSProperties = {
    fontSize: 11,
    color: dark.textSub,
    marginBottom: 6,
    textTransform: "uppercase",
    letterSpacing: "0.04em",
  };

  const labelSubStyle: React.CSSProperties = { fontSize: 11, color: dark.textSub };

  const navBtnStyle: React.CSSProperties = {
    borderRadius: 999,
    border: `1px solid ${dark.border}`,
    background: "#181b24",
    color: dark.textMain,
    fontSize: 12,
    padding: "4px 10px",
    cursor: "pointer",
  };

  return (
    <div style={wrapStyle}>
      <h1 style={{ fontSize: "1.1rem", marginBottom: 4 }}>Digital QC Prototype</h1>
      <p style={{ fontSize: "0.75rem", color: dark.textSub, marginBottom: 10 }}>
        Swipe between dashboard and sheets, check items, and watch completion gauges and time tracking update.
      </p>

      <div
        style={cardStyle}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <div style={stripStyle}>
          {/* ===================== SUMMARY ===================== */}
          <section style={sheetStyle} aria-label="Dashboard Summary">
            <div style={{ display: "flex", justifyContent: "space-between", gap: 10, marginBottom: 10 }}>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                <div style={{ fontWeight: 700, fontSize: 13 }}>QC Dashboard</div>
                <div style={labelSubStyle}>Job: {selectedJobLabel}</div>
                <div style={labelSubStyle}>Tester: {selectedEmployeeLabel}</div>
              </div>
              <div style={{ fontSize: 12, color: dark.flag, fontWeight: 700, textAlign: "right" }}>
                {anyFlag ? "⚠ Flags present" : ""}
              </div>
            </div>

            <div style={{ marginTop: 8, marginBottom: 10 }}>
              <div style={sectionTitleStyle}>Filters</div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center" }}>
                <label style={{ fontSize: 12, color: dark.textSub }}>
                  Job{" "}
                  <select
                    value={jobId}
                    onChange={(e) => setJobId(e.target.value)}
                    style={{
                      marginLeft: 6,
                      background: "#181b24",
                      color: dark.textMain,
                      borderRadius: 6,
                      border: `1px solid ${dark.border}`,
                      padding: "4px 8px",
                      fontSize: 12,
                    }}
                  >
                    {jobs.map((j) => (
                      <option key={j.id} value={j.id}>
                        {j.label}
                      </option>
                    ))}
                  </select>
                </label>

                <label style={{ fontSize: 12, color: dark.textSub }}>
                  Employee{" "}
                  <select
                    value={employeeId}
                    onChange={(e) => setEmployeeId(e.target.value)}
                    style={{
                      marginLeft: 6,
                      background: "#181b24",
                      color: dark.textMain,
                      borderRadius: 6,
                      border: `1px solid ${dark.border}`,
                      padding: "4px 8px",
                      fontSize: 12,
                    }}
                  >
                    {employees.map((emp) => (
                      <option key={emp.id} value={emp.id}>
                        {emp.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>

            <div style={{ marginTop: 8, marginBottom: 10 }}>
              <div style={sectionTitleStyle}>Completion Overview</div>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
                  gap: 10,
                  marginTop: 6,
                }}
              >
                <GaugeCard
                  title="QC-18 Housewire"
                  pct={housewireStats.pct}
                  flagged={housewireStats.flagged}
                  onClick={() => goToIndex(1)}
                />
                <GaugeCard
                  title="QC-18 Integration"
                  pct={integrationStats.pct}
                  flagged={integrationStats.flagged}
                  onClick={() => goToIndex(2)}
                />
                <GaugeCard
                  title="EE Test"
                  pct={eeStats.pct}
                  flagged={eeStats.flagged}
                  onClick={() => goToIndex(3)}
                />
              </div>
            </div>

            <div style={{ marginTop: 8, marginBottom: 10 }}>
              <div style={sectionTitleStyle}>Flags by Sheet</div>
              <ul style={{ listStyle: "none", paddingLeft: 0, fontSize: 12, color: dark.textSub, display: "grid", gap: 6 }}>
                {(["housewire", "integration", "ee"] as const).map((sid) => {
                  const flagged =
                    sid === "housewire"
                      ? housewireStats.flagged
                      : sid === "integration"
                      ? integrationStats.flagged
                      : eeStats.flagged;
                  return (
                    <li key={sid}>
                      {sheetLabels[sid]}:{" "}
                      {flagged ? <span style={{ color: dark.flag, fontWeight: 800 }}>FLAGGED</span> : "Clear"}
                    </li>
                  );
                })}
              </ul>
            </div>

            <div style={{ marginTop: 8, marginBottom: 4 }}>
              <div style={sectionTitleStyle}>Time by Employee (current job)</div>
              <ul style={{ listStyle: "none", paddingLeft: 0, fontSize: 12, color: dark.textSub, display: "grid", gap: 6 }}>
                {timeSummaryRows.length === 0 ? (
                  <li>No time logged yet.</li>
                ) : (
                  timeSummaryRows.map((r, idx) => {
                    const emp = employees.find((e) => e.id === r.emp);
                    const sheetLabel = sheetNames[r.sheetId as SheetId] ?? r.sheetId;
                    return (
                      <li key={`${r.sheetId}-${r.emp}-${idx}`}>
                        {r.emp} – {emp?.name ?? r.emp} – {sheetLabel}: {r.minutes} min
                      </li>
                    );
                  })
                )}
              </ul>
            </div>
          </section>

          {/* ===================== HOUSEWIRE ===================== */}
          <section style={sheetStyle} aria-label="QC-18 Housewire">
            <HeaderBlock
              title="QC-18 Housewire"
              jobLabel={selectedJobLabel}
              employeeLabel={selectedEmployeeLabel}
              flagged={housewireStats.flagged}
            />

            <TimeTracking
              current={formatTime(getStoredSeconds(jobId, "housewire", employeeId))}
              onStart={() => startTimer("housewire")}
              onStop={() => stopTimer()}
              isRunning={
                !!activeTimerRef.current &&
                activeTimerRef.current.jobId === jobId &&
                activeTimerRef.current.sheetId === "housewire" &&
                activeTimerRef.current.employeeId === employeeId
              }
            />

            <Section title="Pre-Test">
              <Checklist items={housewireItems.slice(0, 2)} checkedMap={checks.housewire} onToggle={(id, next) => toggle("housewire", id, next)} />
            </Section>

            <Section title="Wiring Checks">
              <Checklist items={housewireItems.slice(2, 6)} checkedMap={checks.housewire} onToggle={(id, next) => toggle("housewire", id, next)} />
            </Section>

            <Section title="Issues / Flags">
              <Checklist items={housewireItems.slice(6)} checkedMap={checks.housewire} onToggle={(id, next) => toggle("housewire", id, next)} />
              <div style={{ fontSize: 12, color: dark.flag, marginLeft: 22, marginTop: 6 }}>
                Checking this will mark the sheet as <strong>Flagged</strong>.
              </div>
            </Section>
          </section>

          {/* ===================== INTEGRATION ===================== */}
          <section style={sheetStyle} aria-label="QC-18 Integration">
            <HeaderBlock
              title="QC-18 Integration"
              jobLabel={selectedJobLabel}
              employeeLabel={selectedEmployeeLabel}
              flagged={integrationStats.flagged}
            />

            <TimeTracking
              current={formatTime(getStoredSeconds(jobId, "integration", employeeId))}
              onStart={() => startTimer("integration")}
              onStop={() => stopTimer()}
              isRunning={
                !!activeTimerRef.current &&
                activeTimerRef.current.jobId === jobId &&
                activeTimerRef.current.sheetId === "integration" &&
                activeTimerRef.current.employeeId === employeeId
              }
            />

            <Section title="Panel Prep">
              <Checklist items={integrationItems.slice(0, 2)} checkedMap={checks.integration} onToggle={(id, next) => toggle("integration", id, next)} />
            </Section>

            <Section title="Functional Testing">
              <Checklist items={integrationItems.slice(2, 5)} checkedMap={checks.integration} onToggle={(id, next) => toggle("integration", id, next)} />
            </Section>

            <Section title="Issues / Flags">
              <Checklist items={integrationItems.slice(5)} checkedMap={checks.integration} onToggle={(id, next) => toggle("integration", id, next)} />
              <div style={{ fontSize: 12, color: dark.flag, marginLeft: 22, marginTop: 6 }}>
                Checking this will mark the sheet as <strong>Flagged</strong>.
              </div>
            </Section>
          </section>

          {/* ===================== EE ===================== */}
          <section style={sheetStyle} aria-label="EE Test Sheet">
            <HeaderBlock
              title="EE Test Sheet"
              jobLabel={selectedJobLabel}
              employeeLabel={selectedEmployeeLabel}
              flagged={eeStats.flagged}
            />

            <TimeTracking
              current={formatTime(getStoredSeconds(jobId, "ee", employeeId))}
              onStart={() => startTimer("ee")}
              onStop={() => stopTimer()}
              isRunning={
                !!activeTimerRef.current &&
                activeTimerRef.current.jobId === jobId &&
                activeTimerRef.current.sheetId === "ee" &&
                activeTimerRef.current.employeeId === employeeId
              }
            />

            <Section title="Pre-Energization">
              <Checklist items={eeItems.slice(0, 2)} checkedMap={checks.ee} onToggle={(id, next) => toggle("ee", id, next)} />
            </Section>

            <Section title="Electrical Tests">
              <Checklist items={eeItems.slice(2, 5)} checkedMap={checks.ee} onToggle={(id, next) => toggle("ee", id, next)} />
            </Section>

            <Section title="Issues / Flags">
              <Checklist items={eeItems.slice(5)} checkedMap={checks.ee} onToggle={(id, next) => toggle("ee", id, next)} />
              <div style={{ fontSize: 12, color: dark.flag, marginLeft: 22, marginTop: 6 }}>
                Checking this will mark the sheet as <strong>Flagged</strong>.
              </div>
            </Section>
          </section>
        </div>

        {/* Nav row */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginTop: 0,
            padding: "8px 10px 10px",
          }}
        >
          <div style={{ display: "flex", gap: 8 }}>
            <button
              style={{ ...navBtnStyle, opacity: currentIndex === 0 ? 0.4 : 1, cursor: currentIndex === 0 ? "default" : "pointer" }}
              disabled={currentIndex === 0}
              onClick={() => goToIndex(currentIndex - 1)}
            >
              ◀ Prev
            </button>
            <button
              style={{
                ...navBtnStyle,
                opacity: currentIndex === pages.length - 1 ? 0.4 : 1,
                cursor: currentIndex === pages.length - 1 ? "default" : "pointer",
              }}
              disabled={currentIndex === pages.length - 1}
              onClick={() => goToIndex(currentIndex + 1)}
            >
              Next ▶
            </button>
          </div>
          <div style={{ fontSize: 12, color: dark.textSub }}>
            {currentIndex + 1} / {pages.length} – {sheetNames[pages[currentIndex]]}
          </div>
        </div>
      </div>

      <p style={{ marginTop: 8, fontSize: 11, color: dark.textSub }}>
        On a phone, swipe left/right in the panel to move between dashboard and sheets. Tap checkboxes to update completion, flags, and time.
      </p>
    </div>
  );
}

function HeaderBlock({
  title,
  jobLabel,
  employeeLabel,
  flagged,
}: {
  title: string;
  jobLabel: string;
  employeeLabel: string;
  flagged: boolean;
}) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 10, marginBottom: 10 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <div style={{ fontWeight: 700, fontSize: 13 }}>{title}</div>
        <div style={{ fontSize: 11, color: dark.textSub }}>Job: {jobLabel}</div>
        <div style={{ fontSize: 11, color: dark.textSub }}>Tester: {employeeLabel}</div>
      </div>
      <div style={{ fontSize: 12, color: dark.flag, fontWeight: 700, textAlign: "right" }}>
        {flagged ? "⚠ Flagged items present" : ""}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 10, marginBottom: 10 }}>
      <div
        style={{
          fontSize: 11,
          color: dark.textSub,
          marginBottom: 6,
          textTransform: "uppercase",
          letterSpacing: "0.04em",
        }}
      >
        {title}
      </div>
      {children}
    </div>
  );
}

function TimeTracking({
  current,
  onStart,
  onStop,
  isRunning,
}: {
  current: string;
  onStart: () => void;
  onStop: () => void;
  isRunning: boolean;
}) {
  const btnStyle: React.CSSProperties = {
    borderRadius: 999,
    border: `1px solid ${dark.border}`,
    background: "#181b24",
    color: dark.textMain,
    fontSize: 12,
    padding: "3px 9px",
    cursor: "pointer",
  };

  return (
    <div style={{ marginTop: 8, marginBottom: 10 }}>
      <div
        style={{
          fontSize: 11,
          color: dark.textSub,
          marginBottom: 6,
          textTransform: "uppercase",
          letterSpacing: "0.04em",
        }}
      >
        Time Tracking
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button style={{ ...btnStyle, opacity: isRunning ? 0.4 : 1 }} onClick={onStart} disabled={isRunning}>
          Start
        </button>
        <button style={{ ...btnStyle, opacity: !isRunning ? 0.4 : 1 }} onClick={onStop} disabled={!isRunning}>
          Stop
        </button>
        <span style={{ fontSize: 12, color: dark.textSub, minWidth: 52, textAlign: "right" }}>{current}</span>
      </div>
    </div>
  );
}
