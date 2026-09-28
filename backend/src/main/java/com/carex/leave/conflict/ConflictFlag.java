package com.carex.leave.conflict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Latest conflict snapshot per request (implementation.md §10.4). A flag never changes workflow status. */
@Entity
@Table(name = "conflict_flag")
public class ConflictFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "peak_date")
    private LocalDate peakDate;

    @Column(name = "peak_absent_count", nullable = false)
    private int peakAbsentCount;

    @Column(name = "team_size", nullable = false)
    private int teamSize;

    @Column(name = "threshold_pct", nullable = false)
    private BigDecimal thresholdPct;

    @Column(name = "min_absent", nullable = false)
    private int minAbsent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overlapping_request_ids", nullable = false)
    private List<Long> overlappingRequestIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "day_breakdown", nullable = false)
    private List<ConflictResult.DayCount> dayBreakdown;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluated_on", nullable = false)
    private String evaluatedOn;

    @Column(name = "acknowledged_by_manager")
    private Long acknowledgedByManager;

    @Column(name = "acknowledged_by_hr")
    private Long acknowledgedByHr;

    @Version
    private long version;

    protected ConflictFlag() {}

    public ConflictFlag(Long requestId) {
        this.requestId = requestId;
    }

    void apply(ConflictResult r, Instant now, String evaluatedOn) {
        this.flagged = r.flagged();
        this.peakDate = r.peakDate();
        this.peakAbsentCount = r.peakAbsent();
        this.teamSize = r.teamSize();
        this.thresholdPct = r.thresholdPct();
        this.minAbsent = r.minAbsent();
        this.overlappingRequestIds = r.overlappingRequestIds();
        this.dayBreakdown = r.days();
        this.evaluatedAt = now;
        this.evaluatedOn = evaluatedOn;
    }

    void setAcknowledgedByManager(Long id) { this.acknowledgedByManager = id; }
    void setAcknowledgedByHr(Long id) { this.acknowledgedByHr = id; }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public boolean isFlagged() { return flagged; }
    public LocalDate getPeakDate() { return peakDate; }
    public int getPeakAbsentCount() { return peakAbsentCount; }
    public int getTeamSize() { return teamSize; }
    public BigDecimal getThresholdPct() { return thresholdPct; }
    public int getMinAbsent() { return minAbsent; }
    public List<Long> getOverlappingRequestIds() { return overlappingRequestIds; }
    public List<ConflictResult.DayCount> getDayBreakdown() { return dayBreakdown; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public String getEvaluatedOn() { return evaluatedOn; }
    public Long getAcknowledgedByManager() { return acknowledgedByManager; }
    public Long getAcknowledgedByHr() { return acknowledgedByHr; }
}
