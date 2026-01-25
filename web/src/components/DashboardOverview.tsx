import ProjectHeaderCard from "./ProjectHeaderCard";

export function DashboardOverview() {
  return (
    <div style={{ marginTop: 24 }}>
      {/* ✅ Project header panel goes at the top */}
      <div style={{ marginBottom: 16 }}>
        <ProjectHeaderCard />
      </div>

      {/* =====================
          Metrics Row
      ====================== */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: 16,
        }}
      >
        <Metric title="Jobs Active" value="3" />
        <Metric title="Overall Progress" value="68%" />
        <Metric title="Open Issues" value="7" />
        <Metric title="Avg Issue Age" value="2.4 days" />
      </div>
    </div>
  );
}

/* =====================
   Metric Card Component
===================== */
function Metric({ title, value }: { title: string; value: string }) {
  return (
    <div
      style={{
        background: "#f8f9fa",
        borderRadius: 10,
        padding: 16,
        border: "1px solid #ddd",
      }}
    >
      <div style={{ fontSize: 13, opacity: 0.65 }}>{title}</div>
      <div style={{ fontSize: 28, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
