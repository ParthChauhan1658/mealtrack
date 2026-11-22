import type { DailySummary } from "../api";
import type { Loadable } from "../useLoad";

const fmt = new Intl.NumberFormat("en-IN");

export function SummaryCards({ state }: { state: Loadable<DailySummary> }) {
  const s = state.data;
  if (state.error) {
    return (
      <div className="error" role="alert">
        Could not load the summary: {state.error} <button onClick={state.reload}>Retry</button>
      </div>
    );
  }
  const cards = [
    { label: "Total meals", value: s ? fmt.format(s.totalMeals) : "…", sub: s?.date ?? "" },
    {
      label: "Centres reported",
      value: s ? `${fmt.format(s.centresReported)} of ${fmt.format(s.centresActive)}` : "…",
      sub: s ? `${s.coveragePercent.toFixed(1)}% coverage` : "",
    },
    { label: "Open alerts", value: s ? fmt.format(s.openAlerts) : "…", sub: "all dates" },
  ];
  return (
    <div className="cards" aria-busy={state.loading}>
      {cards.map((c) => (
        <div className="card stat" key={c.label}>
          <div className="stat-label">{c.label}</div>
          <div className="stat-value">{c.value}</div>
          <div className="muted">{c.sub}</div>
        </div>
      ))}
    </div>
  );
}
