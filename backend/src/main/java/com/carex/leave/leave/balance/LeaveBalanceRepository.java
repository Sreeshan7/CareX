package com.carex.leave.leave.balance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    /** Race-free lazy creation (implementation.md §9.4). Returns 1 if inserted, 0 if it already existed. */
    @Modifying
    @Query(value = "INSERT INTO leave_balance (user_id, leave_type_code, year, entitled_days, proration_basis) " +
                   "VALUES (:userId, :type, :year, :entitled, :basis) " +
                   "ON CONFLICT (user_id, leave_type_code, year) DO NOTHING", nativeQuery = true)
    int insertIfMissing(@Param("userId") Long userId, @Param("type") String type, @Param("year") int year,
                        @Param("entitled") BigDecimal entitled, @Param("basis") String basis);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.userId = :userId and b.leaveTypeCode = :type and b.year = :year")
    Optional<LeaveBalance> findForUpdate(@Param("userId") Long userId, @Param("type") String type,
                                         @Param("year") int year);

    Optional<LeaveBalance> findByUserIdAndLeaveTypeCodeAndYear(Long userId, String type, int year);

    List<LeaveBalance> findByUserIdAndYearOrderByLeaveTypeCode(Long userId, int year);

    List<LeaveBalance> findByYear(int year);
}
