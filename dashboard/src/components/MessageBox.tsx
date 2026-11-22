import { useState } from "react";
import { ApiError, api, type Parsed, type ParseSaved } from "../api";

type Result =
  | { kind: "saved"; data: ParseSaved }
  | { kind: "review"; parsed?: Parsed; message: string }
  | { kind: "error"; message: string };

const SAMPLES = [
  "aaj 80 bachchon ko khana diya, 5 absent the",
  "Meals served today: 82",
  "served 95 kids, 12 absent, total enrolled 107",
  "khana nahi bana aaj, gas khatam",
];

export function MessageBox({ apiKey, onSaved }: { apiKey: string; onSaved: () => void }) {
  const [centre, setCentre] = useState("GGN-0001");
  const [message, setMessage] = useState(SAMPLES[0]);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  async function submit() {
    setSending(true);
    setResult(null);
    try {
      const data = await api.parse(centre.trim(), message, apiKey);
      setResult({ kind: "saved", data });
      onSaved();
    } catch (e) {
      if (e instanceof ApiError && e.status === 422) {
        setResult({ kind: "review", parsed: e.parsed, message: e.message });
        onSaved(); // a PARSE_UNCERTAIN alert was created
      } else if (e instanceof ApiError && e.status === 401) {
        setResult({ kind: "error", message: "Enter a valid API key above first." });
      } else {
        setResult({ kind: "error", message: (e as Error).message });
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="message-box">
      <div className="row">
        <label>
          Centre code
          <input value={centre} onChange={(e) => setCentre(e.target.value)} spellCheck={false} />
        </label>
      </div>
      <label>
        Message (English / Hindi / Hinglish)
        <textarea rows={3} value={message} onChange={(e) => setMessage(e.target.value)} maxLength={1000} />
      </label>
      <div className="chips">
        {SAMPLES.map((s) => (
          <button key={s} className="chip" onClick={() => setMessage(s)}>
            {s}
          </button>
        ))}
      </div>
      <button className="primary" onClick={submit} disabled={sending || !centre.trim() || !message.trim() || !apiKey}>
        {sending ? "Parsing…" : "Parse and submit"}
      </button>
      {!apiKey && <span className="muted"> Enter the API key above to submit.</span>}

      {result?.kind === "saved" && (
        <ParsedCard
          title={result.data.created ? "Saved (new report)" : "Saved (updated existing report)"}
          tone="ok"
          parsed={result.data}
        />
      )}
      {result?.kind === "review" && (
        <ParsedCard title="Not saved: needs human review" tone="warn" parsed={result.parsed} note={result.message} />
      )}
      {result?.kind === "error" && <div className="error" role="alert">{result.message}</div>}
    </div>
  );
}

function ParsedCard({ title, tone, parsed, note }: { title: string; tone: "ok" | "warn"; parsed?: Parsed; note?: string }) {
  return (
    <div className={`parsed ${tone}`} role="status">
      <strong>{title}</strong>
      {parsed && (
        <dl>
          <dt>Meals served</dt>
          <dd>{parsed.mealsServed ?? "not found"}</dd>
          <dt>Date</dt>
          <dd>{parsed.date}</dd>
          <dt>Parsed by</dt>
          <dd>{parsed.parseMethod}</dd>
          <dt>Confidence</dt>
          <dd>{parsed.confidence}</dd>
        </dl>
      )}
      {note && <div className="muted">{note}</div>}
    </div>
  );
}
