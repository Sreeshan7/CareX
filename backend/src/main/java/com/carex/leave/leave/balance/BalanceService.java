package com.carex.leave.leave.balance;

import com.carex.leave.common.error.Errors;
import com.carex.leave.leave.type.LeaveType;
import com.carex.leave.leave.type.LeaveTypeRepository;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Balance rows: lazily created with pro-rating; mutated only inside workflow transactions while the row is locked.
 * Deltas per event are defined in implementation.md §9.2.
 */
@Service
public class BalanceService {
    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final UserRepository users;

    public BalanceService(LeaveBalanceRepository balances, LeaveTypeRepository types, UserRepository users) {
        this.balances = balances;
        this.types = types;
        this.users = users;
    }

    /** Ensures a row exists for every active type for the given year. Safe under concurrency (ON CONFLICT). */
    @Transactional
    public void ensureRows(Long userId, int year) {
        AppUser user = users.findById(userId).orElseThrow();
        for (LeaveType t : types.findByActiveTrueOrderByCode()) {
            ensureRow(user, t, year);
        }
    }

    private boolean ensureRow(AppUser user, LeaveType type, int year) {
        Optional<ProRatingCalculator.Result> r = ProRatingCalculator.entitlement(
                type.getAnnualEntitlement(), type.isProrated(), user.getJoiningDate(), year);
        if (r.isEmpty()) {
            return false;
        }
        balances.insertIfMissing(user.getId(), type.getCode(), year, r.get().entitled(), r.get().basis());
        return true;
    }

    /** SELECT … FOR UPDATE on the balance row (lock order step 3), creating it first if needed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LeaveBalance lockOrCreate(Long userId, String typeCode, int year) {
        AppUser user = users.findById(userId).orElseThrow();
        LeaveType type = types.findById(typeCode).orElseThrow(() ->
                Errors.businessRule("INVALID_LEAVE_TYPE", "Unknown leave type"));
        if (!ensureRow(user, type, year)) {
            throw Errors.businessRule("BEFORE_JOINING_DATE", "You have no entitlement for " + year);
        }
        return balances.findForUpdate(userId, typeCode, year).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(LeaveBalance b, BigDecimal days) {
        b.reserve(days);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releasePending(LeaveBalance b, BigDecimal days) {
        b.releasePending(days);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitPending(LeaveBalance b, BigDecimal days) {
        b.commitPending(days);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreUsed(LeaveBalance b, BigDecimal days) {
        b.restoreUsed(days);
    }

    @Transactional
    public List<LeaveBalance> balancesFor(Long userId, int year) {
        ensureRows(userId, year);
        return balances.findByUserIdAndYearOrderByLeaveTypeCode(userId, year);
    }

    /** Read-only lookup (no lock) for previews. */
    @Transactional
    public Optional<LeaveBalance> peek(Long userId, String typeCode, int year) {
        AppUser user = users.findById(userId).orElseThrow();
        Optional<LeaveType> type = types.findById(typeCode);
        if (type.isEmpty() || !ensureRow(user, type.get(), year)) {
            return Optional.empty();
        }
        return balances.findByUserIdAndLeaveTypeCodeAndYear(userId, typeCode, year);
    }
}
