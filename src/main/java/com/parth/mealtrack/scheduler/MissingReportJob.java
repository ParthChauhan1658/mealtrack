package com.parth.mealtrack.scheduler;

import com.parth.mealtrack.service.MissingReportService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MissingReportJob {
    private static final Logger log = LoggerFactory.getLogger(MissingReportJob.class);

    private final MissingReportService service;
    private final Clock clock;

    public MissingReportJob(MissingReportService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${mealtrack.anomaly.missing-report-cron}", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        int created = service.raiseFor(today);
        log.info("MissingReportJob for {} created {} alerts", today, created);
    }
}
