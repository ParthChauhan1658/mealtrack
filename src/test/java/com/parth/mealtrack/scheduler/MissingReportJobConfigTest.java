package com.parth.mealtrack.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parth.mealtrack.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;

/** The public demo disables the job with MISSING_REPORT_CRON="-". The app must still start and schedule nothing. */
@SpringBootTest(properties = "mealtrack.anomaly.missing-report-cron=-")
class MissingReportJobConfigTest extends PostgresTestBase {
    @Autowired ScheduledAnnotationBeanPostProcessor scheduler;
    @Autowired MissingReportJob job;

    @Test
    void jobBeanExistsButNoCronTaskIsRegistered() {
        assertTrue(job != null);
        long cronTasks = scheduler.getScheduledTasks().stream().filter(t -> t.getTask() instanceof CronTask).count();
        assertEquals(0, cronTasks);
    }
}
