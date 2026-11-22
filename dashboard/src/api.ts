// Thin typed wrapper over fetch. Same-origin by default (dev proxy / nginx); override with VITE_API_BASE.
const BASE: string = import.meta.env.VITE_API_BASE ?? "";

export type Severity = "LOW" | "MEDIUM" | "HIGH";
export type AlertType =
  | "MISSING_REPORT"
  | "SUDDEN_DROP"
  | "SPIKE"
  | "EXCEEDS_CAPACITY"
  | "ZERO_MEALS"
  | "PARSE_UNCERTAIN";

export interface DistrictSummary {
  district: string;
  meals: number;
  reported: number;
  total: number;
}
export interface DailySummary {
  date: string;
  totalMeals: number;
  centresActive: number;
  centresReported: number;
  coveragePercent: number;
  openAlerts: number;
  byDistrict: DistrictSummary[];
}
export interface TrendPoint {
  date: string;
  totalMeals: number;
  centresReported: number;
}
export interface Alert {
  id: number;
  centreCode: string;
  centreName: string;
  district: string;
  date: string;
  type: AlertType;
  severity: Severity;
  message: string;
  status: "OPEN" | "RESOLVED";
}
export interface Parsed {
  mealsServed: number | null;
  date: string;
  parseMethod: "LLM" | "RULES";
  confidence: "HIGH" | "LOW";
}
export interface ParseSaved extends Parsed {
  centreCode: string;
  created: boolean;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public parsed?: Parsed,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit, apiKey?: string): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (init?.body) headers["Content-Type"] = "application/json";
  if (apiKey) headers["X-API-Key"] = apiKey;
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, "Cannot reach the API. Is the backend running?");
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let parsed: Parsed | undefined;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
      parsed = body.parsed;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, message, parsed);
  }
  return (await res.json()) as T;
}

export const api = {
  daily: (date?: string) => request<DailySummary>(`/api/summary/daily${date ? `?date=${date}` : ""}`),
  trend: (days = 30) => request<TrendPoint[]>(`/api/summary/trend?days=${days}`),
  openAlerts: () => request<Alert[]>("/api/alerts?status=OPEN"),
  resolve: (id: number, apiKey: string) =>
    request<Alert>(`/api/alerts/${id}/resolve`, { method: "PATCH" }, apiKey),
  parse: (centreCode: string, message: string, apiKey: string) =>
    request<ParseSaved>(
      "/api/reports/parse",
      { method: "POST", body: JSON.stringify({ centreCode, message }) },
      apiKey,
    ),
};
