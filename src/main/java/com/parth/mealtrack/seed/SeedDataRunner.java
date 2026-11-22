package com.parth.mealtrack.seed;

import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.seed.SeedDataGenerator.Plan;
import com.parth.mealtrack.service.AnomalyService;
import com.parth.mealtrack.service.MissingReportService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills the database with SYNTHETIC data (profile "seed"). Not real Feeding India data.
 * Skips if centres already exist, unless mealtrack.seed.reset=true.
 */
@Component
@Profile("seed")
public class SeedDataRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private static final long RANDOM_SEED = 20261122L;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbc;
    private final CentreRepository centres;
    private final MealReportRepository reports;
    private final AnomalyService anomalies;
    private final MissingReportService missing;
    private final Clock clock;
    private final ApplicationContext ctx;
    private final boolean reset;
    private final boolean exitWhenDone;

    public SeedDataRunner(JdbcTemplate jdbc, CentreRepository centres, MealReportRepository reports,
                          AnomalyService anomalies, MissingReportService missing, Clock clock, ApplicationContext ctx,
                          @Value("${mealtrack.seed.reset:false}") boolean reset,
                          @Value("${mealtrack.seed.exit-when-done:false}") boolean exitWhenDone) {
        this.jdbc = jdbc;
        this.centres = centres;
        this.reports = reports;
        this.anomalies = anomalies;
        this.missing = missing;
        this.clock = clock;
        this.ctx = ctx;
        this.reset = reset;
        this.exitWhenDone = exitWhenDone;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } finally {
            if (exitWhenDone) System.exit(SpringApplication.exit(ctx));
        }
    }

    void seed() {
        if (centres.count() > 0) {
            if (!reset) {
                log.info("Seed skipped: centres already exist (set mealtrack.seed.reset=true to start over)");
                return;
            }
            jdbc.execute("TRUNCATE alert, meal_report, centre RESTART IDENTITY CASCADE");
        }
        Plan plan = SeedDataGenerator.generate(LocalDate.now(clock), RANDOM_SEED);
        Map<String, Long> idByCode = insertCentres(plan);
        insertReports(plan, idByCode);

        // Run the real anomaly and missing-report logic on the demo day so alerts match the data exactly.
        reports.findAllByReportDate(plan.demoDay()).forEach(anomalies::evaluate);
        int missingAlerts = missing.raiseFor(plan.demoDay());

        log.info("Seeded {} centres, {} reports, demo day {} ({} MISSING_REPORT alerts). All data is synthetic.",
                plan.centres().size(), plan.reports().size(), plan.demoDay(), missingAlerts);
    }

    private Map<String, Long> insertCentres(Plan plan) {
        jdbc.batchUpdate("INSERT INTO centre (code, name, type, district, block, expected_meals, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, true)",
                plan.centres(), 500, (ps, c) -> {
                    ps.setString(1, c.code());
                    ps.setString(2, c.name());
                    ps.setString(3, c.type().name());
                    ps.setString(4, c.district());
                    ps.setString(5, c.block());
                    ps.setInt(6, c.expectedMeals());
                });
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, code FROM centre", rs -> {
            ids.put(rs.getString("code"), rs.getLong("id"));
        });
        return ids;
    }

    private void insertReports(Plan plan, Map<String, Long> idByCode) {
        List<SeedDataGenerator.ReportSpec> specs = plan.reports();
        jdbc.batchUpdate("INSERT INTO meal_report (centre_id, report_date, meals_served, source, parse_method, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 'SEED', 'NONE', ?, ?)",
                specs, 1000, (ps, r) -> {
                    // Stamp each report at 12:00 IST on its own day so created_at looks realistic.
                    Timestamp ts = Timestamp.from(r.date().atTime(LocalTime.NOON).atZone(IST).toInstant());
                    ps.setLong(1, idByCode.get(plan.centres().get(r.centreIdx()).code()));
                    ps.setObject(2, r.date());
                    ps.setInt(3, r.meals());
                    ps.setTimestamp(4, ts);
                    ps.setTimestamp(5, ts);
                });
    }
}
