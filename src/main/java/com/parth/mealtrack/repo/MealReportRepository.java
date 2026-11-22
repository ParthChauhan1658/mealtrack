package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.MealReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealReportRepository extends JpaRepository<MealReport, Long> {
    @EntityGraph(attributePaths = "centre")
    Optional<MealReport> findByCentreIdAndReportDate(Long centreId, LocalDate date);

    @Query("select r from MealReport r join fetch r.centre where r.centre.id = :centreId "
            + "and r.reportDate >= :from and r.reportDate <= :to order by r.reportDate")
    List<MealReport> findHistory(@Param("centreId") Long centreId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
