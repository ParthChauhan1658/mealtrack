package com.parth.mealtrack.service;

import com.parth.mealtrack.domain.Alert;
import com.parth.mealtrack.domain.AlertStatus;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
    private static final int MAX_RESULTS = 500;
    private final AlertRepository alerts;

    public AlertService(AlertRepository alerts) {
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<Alert> search(AlertStatus status, AlertType type, String district, LocalDate date) {
        Specification<Alert> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class) root.fetch("centre"); // skip for the count query
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (type != null) ps.add(cb.equal(root.get("type"), type));
            if (date != null) ps.add(cb.equal(root.get("reportDate"), date));
            if (district != null && !district.isBlank()) ps.add(cb.equal(root.get("centre").get("district"), district));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var page = PageRequest.of(0, MAX_RESULTS, Sort.by(Sort.Direction.DESC, "reportDate", "id"));
        return alerts.findAll(spec, page).getContent();
    }

    /** Idempotent: resolving an already resolved alert returns it unchanged. */
    @Transactional
    public Alert resolve(Long id) {
        Alert a = alerts.findById(id).orElseThrow(() -> ApiException.notFound("Alert not found: " + id));
        if (a.getStatus() == AlertStatus.OPEN) a.resolve();
        a.getCentre().getCode(); // load while the session is open
        return a;
    }
}
