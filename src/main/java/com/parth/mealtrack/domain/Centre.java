package com.parth.mealtrack.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "centre")
public class Centre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CentreType type;

    @Column(nullable = false, length = 60)
    private String district;

    @Column(nullable = false, length = 60)
    private String block;

    @Column(name = "expected_meals", nullable = false)
    private int expectedMeals;

    @Column(nullable = false)
    private boolean active = true;

    protected Centre() {}

    public Centre(String code, String name, CentreType type, String district, String block, int expectedMeals) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.district = district;
        this.block = block;
        this.expectedMeals = expectedMeals;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public CentreType getType() { return type; }
    public String getDistrict() { return district; }
    public String getBlock() { return block; }
    public int getExpectedMeals() { return expectedMeals; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
