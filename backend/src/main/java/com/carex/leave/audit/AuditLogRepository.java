package com.carex.leave.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);

    long countByAction(String action);
}
