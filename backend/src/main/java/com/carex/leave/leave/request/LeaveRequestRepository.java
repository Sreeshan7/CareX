package com.carex.leave.leave.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    /** Lock order step 2 (implementation.md §8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    Optional<LeaveRequest> findByEmployeeIdAndClientRequestId(Long employeeId, UUID clientRequestId);

    Page<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdAndStatusInOrderByStartDateDesc(Long employeeId, Collection<LeaveStatus> statuses,
                                                                      Pageable pageable);

    @Query("select r from LeaveRequest r where r.employeeId = :emp and r.status in :statuses " +
           "and r.startDate <= :end and r.endDate >= :start")
    List<LeaveRequest> findOverlappingForEmployee(@Param("emp") Long employeeId, @Param("statuses") Collection<LeaveStatus> statuses,
                                                  @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select r from LeaveRequest r where r.teamId = :team and r.status in :statuses " +
           "and r.startDate <= :end and r.endDate >= :start")
    List<LeaveRequest> findTeamOverlapping(@Param("team") Long teamId, @Param("statuses") Collection<LeaveStatus> statuses,
                                           @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select r from LeaveRequest r where r.teamId in :teams and r.status in :statuses " +
           "and r.startDate <= :end and r.endDate >= :start order by r.startDate")
    List<LeaveRequest> findTeamsOverlapping(@Param("teams") Collection<Long> teamIds, @Param("statuses") Collection<LeaveStatus> statuses,
                                            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Manager queue: pending manager-stage items assigned to me or escalated to me. */
    @Query("select r from LeaveRequest r where r.status in :statuses and r.employeeId <> :me and " +
           "(r.managerApproverId = :me or r.escalationApproverId = :me) order by r.stageEnteredAt")
    List<LeaveRequest> findManagerQueue(@Param("me") Long me, @Param("statuses") Collection<LeaveStatus> statuses);

    /** HR queue: HR stage + manager-stage items routed/escalated to the HR pool. */
    @Query("select r from LeaveRequest r where r.employeeId <> :me and (r.status in :hrStatuses or " +
           "(r.status in :mgrStatuses and (r.escalatedToHrPool = true or r.managerRoutedToHr = true))) " +
           "order by r.stageEnteredAt")
    List<LeaveRequest> findHrQueue(@Param("me") Long me, @Param("hrStatuses") Collection<LeaveStatus> hrStatuses,
                                   @Param("mgrStatuses") Collection<LeaveStatus> mgrStatuses);

    List<LeaveRequest> findByStatusIn(Collection<LeaveStatus> statuses);

    List<LeaveRequest> findByIdIn(Collection<Long> ids);

    /**
     * Escalation claim (implementation.md §11.3): one overdue row, skipping rows locked by concurrent
     * approvals or another scheduler instance.
     */
    @Query(value = "SELECT id FROM leave_request WHERE status IN ('PENDING_MANAGER','PENDING_HR') " +
                   "AND stage_deadline_at <= :now AND NOT (id = ANY(CAST(:excluded AS bigint[]))) " +
                   "ORDER BY stage_deadline_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Long> claimOverdue(@Param("now") Instant now, @Param("excluded") String excludedArrayLiteral);

    /** Lock order step 1: serializes submissions per team so conflict evaluation sees each other (§10.3). */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(4242, CAST(:teamId AS int))) AS l", nativeQuery = true)
    Integer lockTeam(@Param("teamId") Long teamId);

    long countByStatus(LeaveStatus status);

    /** Demo only: simulate an overdue stage (used by the demo seeder to show escalation immediately). */
    @Modifying
    @Query("update LeaveRequest r set r.stageDeadlineAt = :deadline where r.id = :id")
    int forceDeadline(@Param("id") Long id, @Param("deadline") Instant deadline);
}
