import { useMemo, useState } from "react";
import type { DistrictSummary } from "../api";

type Key = "district" | "meals" | "reported" | "coverage";

export const coverage = (d: DistrictSummary) => (d.total === 0 ? 0 : (d.reported / d.total) * 100);

export function sortDistricts(rows: DistrictSummary[], key: Key, asc: boolean): DistrictSummary[] {
  const value = (d: DistrictSummary) => (key === "coverage" ? coverage(d) : d[key]);
  return [...rows].sort((a, b) => {
    const x = value(a);
    const y = value(b);
    const c = typeof x === "string" ? x.localeCompare(y as string) : (x as number) - (y as number);
    return asc ? c : -c;
  });
}

export function DistrictTable({ rows }: { rows: DistrictSummary[] }) {
  const [key, setKey] = useState<Key>("district");
  const [asc, setAsc] = useState(true);
  const sorted = useMemo(() => sortDistricts(rows, key, asc), [rows, key, asc]);

  const head = (k: Key, label: string, numeric = true) => (
    <th
      className={numeric ? "num" : undefined}
      aria-sort={key === k ? (asc ? "ascending" : "descending") : "none"}
    >
      <button
        className="th-button"
        onClick={() => {
          if (k === key) setAsc(!asc);
          else {
            setKey(k);
            setAsc(k === "district");
          }
        }}
      >
        {label} {key === k ? (asc ? "▲" : "▼") : ""}
      </button>
    </th>
  );

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {head("district", "District", false)}
            {head("meals", "Meals")}
            {head("reported", "Reported / total")}
            {head("coverage", "Coverage")}
          </tr>
        </thead>
        <tbody>
          {sorted.map((d) => (
            <tr key={d.district}>
              <td>{d.district}</td>
              <td className="num">{d.meals.toLocaleString("en-IN")}</td>
              <td className="num">
                {d.reported} / {d.total}
              </td>
              <td className="num">{coverage(d).toFixed(1)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
