import { describe, expect, it } from "vitest";
import type { Alert, DistrictSummary, TrendPoint } from "./api";
import { sortAlerts } from "./components/AlertsTable";
import { coverage, sortDistricts } from "./components/DistrictTable";
import { workingDays } from "./components/TrendChart";
import { latestDayWithData } from "./dates";

const district = (d: string, meals: number, reported: number, total: number): DistrictSummary => ({
  district: d,
  meals,
  reported,
  total,
});

describe("district table", () => {
  const rows = [district("B", 100, 8, 10), district("A", 300, 5, 5), district("C", 200, 1, 4)];
  it("sorts by name ascending", () => expect(sortDistricts(rows, "district", true).map((r) => r.district)).toEqual(["A", "B", "C"]));
  it("sorts by meals descending", () => expect(sortDistricts(rows, "meals", false).map((r) => r.district)).toEqual(["A", "C", "B"]));
  it("sorts by coverage", () => expect(sortDistricts(rows, "coverage", false).map((r) => r.district)).toEqual(["A", "B", "C"]));
  it("does not mutate its input", () => {
    sortDistricts(rows, "meals", true);
    expect(rows[0].district).toBe("B");
  });
  it("coverage of an empty district is 0, not NaN", () => expect(coverage(district("X", 0, 0, 0))).toBe(0));
});

describe("alerts table", () => {
  const alert = (id: number, severity: Alert["severity"], date: string): Alert => ({
    id, centreCode: `C-${id}`, centreName: "n", district: "d", date, type: "SPIKE", severity, message: "m", status: "OPEN",
  });
  it("puts HIGH first, then newest date", () => {
    const sorted = sortAlerts([alert(1, "LOW", "2026-11-21"), alert(2, "HIGH", "2026-11-19"), alert(3, "HIGH", "2026-11-21"), alert(4, "MEDIUM", "2026-11-21")]);
    expect(sorted.map((a) => a.id)).toEqual([3, 2, 4, 1]);
  });
});

describe("trend", () => {
  const p = (date: string, totalMeals: number): TrendPoint => ({ date, totalMeals, centresReported: 0 });
  it("drops Sundays from the chart", () => {
    // 2026-11-22 is a Sunday
    expect(workingDays([p("2026-11-21", 5), p("2026-11-22", 0), p("2026-11-23", 7)]).map((x) => x.date)).toEqual(["2026-11-21", "2026-11-23"]);
  });
  it("opens on the latest day with data, skipping empty days", () => {
    expect(latestDayWithData([p("2026-11-20", 9), p("2026-11-21", 5), p("2026-11-22", 0)])).toBe("2026-11-21");
  });
  it("has no default when nothing has data", () => expect(latestDayWithData([p("2026-11-22", 0)])).toBeUndefined());
});
