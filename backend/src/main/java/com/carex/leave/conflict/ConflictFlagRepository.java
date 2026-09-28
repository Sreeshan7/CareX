package com.carex.leave.conflict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConflictFlagRepository extends JpaRepository<ConflictFlag, Long> {
    Optional<ConflictFlag> findByRequestId(Long requestId);

    List<ConflictFlag> findByRequestIdIn(Collection<Long> requestIds);

    List<ConflictFlag> findByFlaggedTrue();
}
