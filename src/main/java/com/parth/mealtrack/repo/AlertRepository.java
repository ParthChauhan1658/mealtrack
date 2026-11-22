package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.Alert;
import com.parth.mealtrack.domain.AlertType;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    /**
     * Race-safe create. A second call for the same (centre, date, type) never inserts a duplicate: an OPEN alert
     * gets its severity/message refreshed, a RESOLVED one is left alone.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO alert (centre_id, report_date, type, severity, message, status, created_at)
            VALUES (:centreId, :date, :type, :severity, :message, 'OPEN', now())
            ON CONFLICT (centre_id, report_date, type)
            DO UPDATE SET severity = EXCLUDED.severity, message = EXCLUDED.message
            WHERE alert.status = 'OPEN'
            """, nativeQuery = true)
    int upsertOpen(@Param("centreId") long centreId, @Param("date") LocalDate date, @Param("type") String type,
                   @Param("severity") String severity, @Param("message") String message);

    /** Create only if absent; never touches an existing row (used by the missing-report job). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO alert (centre_id, report_date, type, severity, message, status, created_at)
            VALUES (:centreId, :date, :type, :severity, :message, 'OPEN', now())
            ON CONFLICT (centre_id, report_date, type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("centreId") long centreId, @Param("date") LocalDate date, @Param("type") String type,
                       @Param("severity") String severity, @Param("message") String message);

    @Transactional
    @Modifying
    @Query("""
            update Alert a set a.status = com.parth.mealtrack.domain.AlertStatus.RESOLVED, a.resolvedAt = current_timestamp
            where a.centre.id = :centreId and a.reportDate = :date and a.type in :types
            and a.status = com.parth.mealtrack.domain.AlertStatus.OPEN
            """)
    int resolveOpen(@Param("centreId") Long centreId, @Param("date") LocalDate date,
                    @Param("types") Collection<AlertType> types);
}
