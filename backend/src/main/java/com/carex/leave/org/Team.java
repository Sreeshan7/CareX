package com.carex.leave.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "team")
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "conflict_threshold_pct", nullable = false)
    private BigDecimal conflictThresholdPct = new BigDecimal("30.00");

    @Column(name = "conflict_min_absent", nullable = false)
    private int conflictMinAbsent = 2;

    protected Team() {}

    public Team(String name, BigDecimal thresholdPct, int minAbsent) {
        this.name = name;
        this.conflictThresholdPct = thresholdPct;
        this.conflictMinAbsent = minAbsent;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public BigDecimal getConflictThresholdPct() { return conflictThresholdPct; }
    public int getConflictMinAbsent() { return conflictMinAbsent; }
}
