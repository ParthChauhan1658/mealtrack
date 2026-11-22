import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TrendPoint } from "../api";

/** Centres do not report on Sundays, so those zero days are left out of the line. */
export function workingDays(points: TrendPoint[]): TrendPoint[] {
  return points.filter((p) => new Date(p.date + "T00:00:00").getDay() !== 0);
}

const short = (d: string) => d.slice(5); // MM-DD

export function TrendChart({ points }: { points: TrendPoint[] }) {
  const data = workingDays(points);
  return (
    <div className="chart" role="img" aria-label="Total meals per day over the last 30 days">
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis dataKey="date" tickFormatter={short} stroke="var(--muted)" fontSize={12} minTickGap={24} />
          <YAxis stroke="var(--muted)" fontSize={12} width={64} tickFormatter={(v: number) => v.toLocaleString("en-IN")} />
          <Tooltip
            formatter={(v: number) => [v.toLocaleString("en-IN"), "Meals"]}
            labelFormatter={(l: string) => l}
            contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", color: "var(--text)" }}
          />
          <Line type="monotone" dataKey="totalMeals" stroke="var(--accent)" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
