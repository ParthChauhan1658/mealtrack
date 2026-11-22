package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.Centre;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CentreRepository extends JpaRepository<Centre, Long>, JpaSpecificationExecutor<Centre> {
    Optional<Centre> findByCode(String code);

    boolean existsByCode(String code);
}
