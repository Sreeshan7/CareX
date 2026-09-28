package com.carex.leave.leave.balance;

import com.carex.leave.common.error.Errors;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * implementation.md §9.1: available = entitled + adjustment − used − pending.
 * Mutators are package-private-ish by convention: only BalanceService (called by the workflow) uses them —
 * enforced by an ArchUnit test. The DB CHECK ck_balance_not_overdrawn is the final backstop.
 */
@Entity
@Table(name = "leave_balance")
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_code", nullable = false)
    private String leaveTypeCode;

    @Column(nullable = false)
    private int year;

    @Column(name = "entitled_days", nullable = false)
    private BigDecimal entitledDays;

    @Column(name = "adjustment_days", nullable = false)
    private BigDecimal adjustmentDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "pending_days", nullable = false)
    private BigDecimal pendingDays = BigDecimal.ZERO;

    @Column(name = "proration_basis", nullable = false)
    private String prorationBasis;

    @Version
    private long version;

    protected LeaveBalance() {}

    public BigDecimal available() {
        return entitledDays.add(adjustmentDays).subtract(usedDays).subtract(pendingDays);
    }

    public BigDecimal remainingAfterPending() {
        return entitledDays.add(adjustmentDays).subtract(usedDays);
    }

    void reserve(BigDecimal days) {
        if (available().compareTo(days) < 0) {
            throw Errors.businessRule("INSUFFICIENT_BALANCE",
                    "Available " + available() + " days, requested " + days)
                    .with("available", available()).with("requested", days);
        }
        pendingDays = pendingDays.add(days);
    }

    void releasePending(BigDecimal days) {
        pendingDays = requireNonNegative(pendingDays.subtract(days));
    }

    void commitPending(BigDecimal days) {
        pendingDays = requireNonNegative(pendingDays.subtract(days));
        usedDays = usedDays.add(days);
    }

    void restoreUsed(BigDecimal days) {
        usedDays = requireNonNegative(usedDays.subtract(days));
    }

    private static BigDecimal requireNonNegative(BigDecimal v) {
        if (v.signum() < 0) {
            throw new IllegalStateException("Balance invariant violated: negative value");
        }
        return v;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public int getYear() { return year; }
    public BigDecimal getEntitledDays() { return entitledDays; }
    public BigDecimal getAdjustmentDays() { return adjustmentDays; }
    public BigDecimal getUsedDays() { return usedDays; }
    public BigDecimal getPendingDays() { return pendingDays; }
    public String getProrationBasis() { return prorationBasis; }
    public long getVersion() { return version; }
}
