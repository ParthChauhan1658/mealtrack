package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.Centre;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CentreRepository extends JpaRepository<Centre, Long>, JpaSpecificationExecutor<Centre> {
    Optional<Centre> findByCode(String code);

    boolean existsByCode(String code);

    /** Per-district totals over active centres; districts with no reports still appear (meals 0). */
    @Query("""
            select c.district as district, coalesce(sum(r.mealsServed), 0L) as meals,
                   count(r) as reported, count(c) as total
            from Centre c left join MealReport r on r.centre = c and r.reportDate = :date
            where c.active = true
            group by c.district order by c.district
            """)
    List<DistrictRow> districtSummary(@Param("date") LocalDate date);
}
