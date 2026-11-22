package com.parth.mealtrack.service;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.MealReport;
import com.parth.mealtrack.domain.ParseMethod;
import com.parth.mealtrack.domain.ReportSource;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportService {
    /** Outcome of a submit: the stored report and whether it was newly created. */
    public record SubmitResult(MealReport report, boolean created) {}

    private final CentreRepository centres;
    private final MealReportRepository reports;
    private final MealtrackProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReportService(CentreRepository centres, MealReportRepository reports, MealtrackProperties props,
                         Clock clock, PlatformTransactionManager txManager) {
        this.centres = centres;
        this.reports = reports;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    public SubmitResult submit(String centreCode, LocalDate date, int meals, ReportSource source,
                               String rawMessage, ParseMethod parseMethod) {
        Centre centre = centres.findByCode(centreCode)
                .orElseThrow(() -> ApiException.badRequest("Unknown centre code: " + centreCode));
        validate(centre, date, meals);
        try {
            return tx.execute(s -> upsert(centre, date, meals, source, rawMessage, parseMethod));
        } catch (DataIntegrityViolationException race) {
            // A concurrent request inserted the same (centre, date) first. Retry; it now finds the row and updates.
            return tx.execute(s -> upsert(centre, date, meals, source, rawMessage, parseMethod));
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MealReport> history(String centreCode, LocalDate from, LocalDate to) {
        Centre centre = centres.findByCode(centreCode)
                .orElseThrow(() -> ApiException.badRequest("Unknown centre code: " + centreCode));
        return reports.findHistory(centre.getId(), from, to);
    }

    /** Rules from spec section 7. Also used to validate LLM output. */
    public void validate(Centre centre, LocalDate date, int meals) {
        if (!centre.isActive()) {
            throw ApiException.badRequest("Centre is inactive: " + centre.getCode());
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw ApiException.badRequest("Date cannot be in the future: " + date);
        }
        if (date.isBefore(today.minusDays(props.validation().maxAgeDays()))) {
            throw ApiException.badRequest("Reports older than " + props.validation().maxAgeDays() + " days are rejected: " + date);
        }
        if (meals < 0) {
            throw ApiException.badRequest("mealsServed must be >= 0");
        }
        double limit = centre.getExpectedMeals() * props.validation().rejectFactor();
        if (meals > limit) {
            throw ApiException.badRequest("mealsServed " + meals + " exceeds " + props.validation().rejectFactor()
                    + "x expected (" + centre.getExpectedMeals() + "); probable typo");
        }
    }

    private SubmitResult upsert(Centre centre, LocalDate date, int meals, ReportSource source,
                                String rawMessage, ParseMethod parseMethod) {
        var existing = reports.findByCentreIdAndReportDate(centre.getId(), date);
        boolean created = existing.isEmpty();
        MealReport report = existing.orElseGet(() -> new MealReport(centre, date));
        report.setMealsServed(meals);
        report.setSource(source);
        report.setRawMessage(rawMessage);
        report.setParseMethod(parseMethod);
        // saveAndFlush so a unique violation surfaces inside this transaction template, where we can retry.
        return new SubmitResult(reports.saveAndFlush(report), created);
    }
}
