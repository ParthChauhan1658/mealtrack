/** The newest day that actually has reports (so a Sunday does not open on an empty page). */
export function latestDayWithData(points: { date: string; totalMeals: number }[]): string | undefined {
  return [...points].reverse().find((p) => p.totalMeals > 0)?.date;
}
