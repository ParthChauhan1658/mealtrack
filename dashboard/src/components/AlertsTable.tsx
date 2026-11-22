import { useState } from "react";
import { type Alert, ApiError, api, type Severity } from "../api";

const RANK: Record<Severity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

/** Most severe first, then newest date, then centre code. */
export function sortAlerts(alerts: Alert[]): Alert[] {
  return [...alerts].sort(
    (a, b) =>
      RANK[a.severity] - RANK[b.severity] ||
      b.date.localeCompare(a.date) ||
      a.centreCode.localeCompare(b.centreCode),
  );
}

const SHOWN = 100;

export function AlertsTable({
  alerts,
  apiKey,
  onResolved,
}: {
  alerts: Alert[];
  apiKey: string;
  onResolved: () => void;
}) {
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const sorted = sortAlerts(alerts);

  async function resolve(id: number) {
    setBusy(id);
    setError(null);
    try {
      await api.resolve(id, apiKey);
      onResolved();
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? "Enter a valid API key above to resolve alerts." : (e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  if (alerts.length === 0) return <div className="muted">No open alerts.</div>;
  return (
    <>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="table-wrap tall">
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Severity</th>
              <th>Centre</th>
              <th>Date</th>
              <th>Message</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {sorted.slice(0, SHOWN).map((a) => (
              <tr key={a.id}>
                <td>{a.type.replace("_", " ")}</td>
                <td>
                  <span className={`badge ${a.severity.toLowerCase()}`}>{a.severity}</span>
                </td>
                <td>
                  <div>{a.centreCode}</div>
                  <div className="muted">{a.district}</div>
                </td>
                <td>{a.date}</td>
                <td className="msg">{a.message}</td>
                <td>
                  <button disabled={busy === a.id || !apiKey} title={apiKey ? "" : "Enter the API key first"} onClick={() => resolve(a.id)}>
                    {busy === a.id ? "…" : "Resolve"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {sorted.length > SHOWN && (
        <div className="muted">Showing the {SHOWN} most severe of {sorted.length} open alerts.</div>
      )}
    </>
  );
}
