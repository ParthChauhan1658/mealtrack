package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.MealReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealReportRepository extends JpaRepository<MealReport, Long> {
    Optional<MealReport> findByCentreIdAndReportDate(Long centreId, LocalDate date);
}
