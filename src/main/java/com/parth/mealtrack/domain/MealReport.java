package com.parth.mealtrack.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_report")
public class MealReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centre_id")
    private Centre centre;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "meals_served", nullable = false)
    private int mealsServed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportSource source;

    @Column(name = "raw_message", columnDefinition = "text")
    private String rawMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_method", nullable = false, length = 20)
    private ParseMethod parseMethod = ParseMethod.NONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MealReport() {}

    public MealReport(Centre centre, LocalDate reportDate) {
        this.centre = centre;
        this.reportDate = reportDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Centre getCentre() { return centre; }
    public LocalDate getReportDate() { return reportDate; }
    public int getMealsServed() { return mealsServed; }
    public void setMealsServed(int mealsServed) { this.mealsServed = mealsServed; }
    public ReportSource getSource() { return source; }
    public void setSource(ReportSource source) { this.source = source; }
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }
    public ParseMethod getParseMethod() { return parseMethod; }
    public void setParseMethod(ParseMethod parseMethod) { this.parseMethod = parseMethod; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
