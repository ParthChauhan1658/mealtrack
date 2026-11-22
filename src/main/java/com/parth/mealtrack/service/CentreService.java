package com.parth.mealtrack.service;

import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.web.ApiException;
import com.parth.mealtrack.web.CentreRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CentreService {
    private final CentreRepository centres;

    public CentreService(CentreRepository centres) {
        this.centres = centres;
    }

    @Transactional
    public Centre create(CentreRequest r) {
        if (centres.existsByCode(r.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Centre code already exists: " + r.code());
        }
        return centres.save(new Centre(r.code(), r.name(), r.type(), r.district(), r.block(), r.expectedMeals()));
    }

    @Transactional(readOnly = true)
    public Centre get(Long id) {
        return centres.findById(id).orElseThrow(() -> ApiException.notFound("Centre not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Centre> search(String district, Boolean active, Pageable pageable) {
        Specification<Centre> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (district != null && !district.isBlank()) ps.add(cb.equal(root.get("district"), district));
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return centres.findAll(spec, pageable);
    }
}
