package com.parth.mealtrack.seed;

import com.parth.mealtrack.domain.CentreType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the synthetic dataset in memory. Pure and deterministic: the same seed and date always give the same
 * data. All data is fictional; real district names are used only for realism.
 */
public final class SeedDataGenerator {
    public static final int CENTRES = 1000;
    public static final int SCHOOLS = 600; // the rest are Anganwadis (60/40)
    public static final int HISTORY_DAYS = 30;
    public static final double SKIP_RATE = 0.03;
    public static final double NOISE = 0.08;
    public static final double SATURDAY_FACTOR = 0.80;
    public static final int DEMO_DROPS = 10;
    public static final int DEMO_SPIKES = 5;
    public static final int DEMO_ZEROS = 5;
    public static final int DEMO_MISSING = 40;
    /** Of the missing centres, how many also missed the previous working day (so their alert is HIGH). */
    public static final int DEMO_MISSING_REPEAT = 8;

    static final List<String> DISTRICTS = List.of(
            "Gurugram", "Faridabad", "Rohtak", "Sonipat", "Panipat", "Karnal", "Hisar", "South Delhi");
    static final List<String> PREFIXES = List.of("GGN", "FBD", "ROH", "SNP", "PNP", "KNL", "HSR", "SDL");

    public record CentreSpec(String code, String name, CentreType type, String district, String block,
                             int expectedMeals) {}

    /** One day's meals for the centre at {@code centreIdx} (index into {@link Plan#centres()}). */
    public record ReportSpec(int centreIdx, LocalDate date, int meals) {}

    public record Plan(List<CentreSpec> centres, List<ReportSpec> reports, LocalDate demoDay,
                       Set<Integer> missing, Set<Integer> drops, Set<Integer> spikes, Set<Integer> zeros) {}

    private SeedDataGenerator() {}

    /** The last day centres report on: today, or the previous Saturday if today is a Sunday. */
    public static LocalDate demoDay(LocalDate today) {
        return today.getDayOfWeek() == DayOfWeek.SUNDAY ? today.minusDays(1) : today;
    }

    static LocalDate previousWorkingDay(LocalDate d) {
        LocalDate p = d.minusDays(1);
        return p.getDayOfWeek() == DayOfWeek.SUNDAY ? p.minusDays(1) : p;
    }

    public static Plan generate(LocalDate today, long seed) {
        Random rnd = new Random(seed);
        List<CentreSpec> centres = makeCentres(rnd);
        LocalDate demoDay = demoDay(today);

        // Pick the demo-day anomaly centres first: disjoint groups from one shuffle.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < centres.size(); i++) order.add(i);
        Collections.shuffle(order, rnd);
        int at = 0;
        Set<Integer> missing = new HashSet<>(order.subList(at, at += DEMO_MISSING));
        Set<Integer> drops = new HashSet<>(order.subList(at, at += DEMO_DROPS));
        Set<Integer> spikes = new HashSet<>(order.subList(at, at += DEMO_SPIKES));
        Set<Integer> zeros = new HashSet<>(order.subList(at, at += DEMO_ZEROS));
        Set<Integer> missedPrevious = new HashSet<>(order.subList(0, DEMO_MISSING_REPEAT));
        LocalDate previous = previousWorkingDay(demoDay);

        List<ReportSpec> reports = new ArrayList<>();
        Map<Integer, List<Integer>> recent = new HashMap<>(); // centre -> its history meals, oldest first
        for (LocalDate d = demoDay.minusDays(HISTORY_DAYS); d.isBefore(demoDay); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            for (int i = 0; i < centres.size(); i++) {
                boolean skipped = rnd.nextDouble() < SKIP_RATE;
                if (skipped || (d.equals(previous) && missedPrevious.contains(i))) continue;
                int meals = dailyMeals(centres.get(i).expectedMeals(), d, rnd);
                reports.add(new ReportSpec(i, d, meals));
                recent.computeIfAbsent(i, k -> new ArrayList<>()).add(meals);
            }
        }

        for (int i = 0; i < centres.size(); i++) {
            if (missing.contains(i)) continue;
            int meals;
            if (zeros.contains(i)) {
                meals = 0;
            } else if (drops.contains(i)) {
                meals = (int) Math.max(1, Math.round(average(recent.get(i)) * (0.25 + 0.30 * rnd.nextDouble())));
            } else if (spikes.contains(i)) {
                meals = (int) Math.round(average(recent.get(i)) * (1.50 + 0.20 * rnd.nextDouble()));
            } else {
                meals = dailyMeals(centres.get(i).expectedMeals(), demoDay, rnd);
            }
            reports.add(new ReportSpec(i, demoDay, meals));
        }
        return new Plan(centres, reports, demoDay, missing, drops, spikes, zeros);
    }

    private static List<CentreSpec> makeCentres(Random rnd) {
        List<CentreType> types = new ArrayList<>();
        for (int i = 0; i < CENTRES; i++) types.add(i < SCHOOLS ? CentreType.SCHOOL : CentreType.ANGANWADI);
        Collections.shuffle(types, rnd);

        int[] perDistrict = new int[DISTRICTS.size()];
        List<CentreSpec> centres = new ArrayList<>();
        for (int i = 0; i < CENTRES; i++) {
            int d = i % DISTRICTS.size();
            int n = ++perDistrict[d];
            CentreType type = types.get(i);
            String block = DISTRICTS.get(d) + " Block " + (1 + rnd.nextInt(5));
            String kind = type == CentreType.SCHOOL ? "Govt. School" : "Anganwadi Centre";
            centres.add(new CentreSpec(String.format("%s-%04d", PREFIXES.get(d), n), kind + " " + block + " #" + n,
                    type, DISTRICTS.get(d), block, expectedMeals(type, rnd)));
        }
        return centres;
    }

    /**
     * Schools 80-400, Anganwadis 25-60. The school curve leans towards smaller schools (exponent 1.25) so that a
     * normal weekday lands near 1.4 lakh meals.
     */
    static int expectedMeals(CentreType type, Random rnd) {
        if (type == CentreType.SCHOOL) return 80 + (int) Math.round(320 * Math.pow(rnd.nextDouble(), 1.25));
        return 25 + rnd.nextInt(36);
    }

    static int dailyMeals(int expected, LocalDate day, Random rnd) {
        double base = day.getDayOfWeek() == DayOfWeek.SATURDAY ? expected * SATURDAY_FACTOR : expected;
        double noise = 1 + (rnd.nextDouble() * 2 - 1) * NOISE;
        return (int) Math.max(1, Math.round(base * noise));
    }

    private static double average(List<Integer> values) {
        int from = Math.max(0, values.size() - 7);
        return values.subList(from, values.size()).stream().mapToInt(Integer::intValue).average().orElse(0);
    }
}
