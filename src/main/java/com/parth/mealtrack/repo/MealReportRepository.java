package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.MealReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealReportRepository extends JpaRepository<MealReport, Long> {
    @EntityGraph(attributePaths = "centre")
    Optional<MealReport> findByCentreIdAndReportDate(Long centreId, LocalDate date);

    /** Meals of the most recent reports strictly before {@code date}, newest first. */
    @Query("select r.mealsServed from MealReport r where r.centre.id = :centreId and r.reportDate < :date "
            + "order by r.reportDate desc")
    List<Integer> recentMeals(@Param("centreId") Long centreId, @Param("date") LocalDate date, Pageable limit);

    @EntityGraph(attributePaths = "centre")
    List<MealReport> findAllByReportDate(LocalDate date);

    /** Daily totals over active centres; days with no reports are absent from the result. */
    @Query("""
            select r.reportDate as date, sum(r.mealsServed) as meals, count(r) as reported
            from MealReport r where r.centre.active = true and r.reportDate between :from and :to
            group by r.reportDate order by r.reportDate
            """)
    List<TrendRow> dailyTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r from MealReport r join fetch r.centre where r.centre.id = :centreId "
            + "and r.reportDate >= :from and r.reportDate <= :to order by r.reportDate")
    List<MealReport> findHistory(@Param("centreId") Long centreId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
