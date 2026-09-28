package com.carex.leave.workflow.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {
    List<ApprovalAction> findByRequestIdOrderByIdAsc(Long requestId);

    boolean existsByRequestIdAndStageAndActionAndActorId(Long requestId, String stage, String action, Long actorId);

    long countByRequestIdAndStageAndActionIn(Long requestId, String stage, List<String> actions);

    @Query("select a from ApprovalAction a where a.actorId = :actor and a.action in ('APPROVE','REJECT') order by a.id desc")
    List<ApprovalAction> findDecisionsBy(@Param("actor") Long actorId);

    List<ApprovalAction> findByActionIn(List<String> actions);
}
