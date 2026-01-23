export function DashboardOverview() {
  return (
    <div style={{ marginTop: 24 }}>
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

      {/* =====================
          Worker / Labor Table
      ====================== */}
      <div
        style={{
          marginTop: 24,
          border: "1px solid #ddd",
          borderRadius: 10,
          padding: 16,
        }}
      >
        <h3 style={{ marginTop: 0 }}>Labor & Accountability</h3>

        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
            fontSize: 14,
          }}
        >
          <thead>
            <tr style={{ borderBottom: "1px solid #eee" }}>
              <th align="left" style={{ paddingBottom: 8 }}>Worker</th>
              <th align="left" style={{ paddingBottom: 8 }}>Hours Logged</th>
              <th align="left" style={{ paddingBottom: 8 }}>Flags Raised</th>
              <th align="left" style={{ paddingBottom: 8 }}>Current Task</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Alex</td>
              <td>12.5</td>
              <td>2</td>
              <td>House Wire</td>
            </tr>
            <tr>
              <td>Jamie</td>
              <td>9.0</td>
              <td>3</td>
              <td>Panel Integration</td>
            </tr>
            <tr>
              <td>Riley</td>
              <td>14.2</td>
              <td>2</td>
              <td>Testing</td>
            </tr>
          </tbody>
        </table>
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
