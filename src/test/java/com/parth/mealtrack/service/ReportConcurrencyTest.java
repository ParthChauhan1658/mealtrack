package com.parth.mealtrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.CentreType;
import com.parth.mealtrack.domain.ParseMethod;
import com.parth.mealtrack.domain.ReportSource;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReportConcurrencyTest extends PostgresTestBase {
    @Autowired ReportService service;
    @Autowired CentreRepository centres;
    @Autowired MealReportRepository reports;

    @Test
    void simultaneousReportsForSameCentreAndDateLeaveOneRowAndNoErrors() throws Exception {
        Centre c = centres.save(new Centre("RACE-1", "Race", CentreType.SCHOOL, "RaceDistrict", "B", 500));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        int threads = 12;
        var start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<ReportService.SubmitResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int meals = 100 + i;
            futures.add(pool.submit(() -> {
                start.await();
                return service.submit("RACE-1", today, meals, ReportSource.API, null, ParseMethod.NONE);
            }));
        }
        long created = 0;
        for (var f : futures) {
            if (f.get(30, TimeUnit.SECONDS).created()) created++; // any exception fails the test here
        }
        pool.shutdown();

        assertEquals(1, created, "exactly one request should create the row");
        assertEquals(1, reports.findAll().stream().filter(r -> r.getCentre().getId().equals(c.getId())).count());
    }
}
