package com.carex.leave.escalation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {
    Optional<Escalation> findByRequestIdAndStageAndResolvedAtIsNull(Long requestId, String stage);

    List<Escalation> findByRequestIdOrderByIdAsc(Long requestId);

    List<Escalation> findByRequestIdIn(Collection<Long> requestIds);

    List<Escalation> findByResolvedAtIsNullOrderByEscalatedAtDesc();

    List<Escalation> findAllByOrderByEscalatedAtDesc();

    long countByRequestIdAndStage(Long requestId, String stage);
}
