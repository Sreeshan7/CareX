package com.carex.leave.leave.type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_type")
public class LeaveType {
    @Id
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "annual_entitlement", nullable = false)
    private BigDecimal annualEntitlement;

    @Column(nullable = false)
    private boolean prorated;

    @Column(name = "backdate_days_allowed", nullable = false)
    private int backdateDaysAllowed;

    @Column(nullable = false)
    private boolean active;

    protected LeaveType() {}

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getAnnualEntitlement() { return annualEntitlement; }
    public boolean isProrated() { return prorated; }
    public int getBackdateDaysAllowed() { return backdateDaysAllowed; }
    public boolean isActive() { return active; }
}
