package com.carex.leave.audit;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.common.web.ClientChannel;
import com.carex.leave.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Writes audit rows in the caller's transaction (commit together or roll back together).
 * Denials use REQUIRES_NEW because the business transaction rolls back (implementation.md §15.2).
 */
@Service
public class AuditService {
    private final AuditLogRepository repo;
    private final BusinessCalendar calendar;

    public AuditService(AuditLogRepository repo, BusinessCalendar calendar) {
        this.repo = repo;
        this.calendar = calendar;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(CurrentUser actor, AuditAction action, String entityType, Long entityId, String summary,
                       Map<String, Object> before, Map<String, Object> after) {
        write(actor, actor == null ? "SYSTEM" : actor.role().name(), action, entityType, entityId, summary, before, after);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSystem(AuditAction action, String entityType, Long entityId, String summary,
                             Map<String, Object> before, Map<String, Object> after) {
        write(null, "SYSTEM", action, entityType, entityId, summary, before, after);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIsolated(CurrentUser actor, String actorRoleIfAnonymous, AuditAction action, String entityType,
                               Long entityId, String summary, Map<String, Object> after) {
        write(actor, actor == null ? actorRoleIfAnonymous : actor.role().name(), action, entityType, entityId,
                summary, null, after);
    }

    private void write(CurrentUser actor, String role, AuditAction action, String entityType, Long entityId,
                       String summary, Map<String, Object> before, Map<String, Object> after) {
        ClientChannel ch = ClientChannel.current();
        String channel = actor == null && ch == ClientChannel.SYSTEM ? "SYSTEM" : ch.name();
        String s = summary.length() > 500 ? summary.substring(0, 500) : summary;
        repo.save(new AuditLog(calendar.now(), actor == null ? null : actor.id(), role, channel, action.name(),
                entityType, entityId, s, before, after, MDC.get(CorrelationIdFilter.MDC_KEY), ClientChannel.currentIp()));
    }
}
