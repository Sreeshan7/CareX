package com.carex.leave.demo;

import com.carex.leave.audit.AuditAction;
import com.carex.leave.audit.AuditService;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.Errors;
import com.carex.leave.config.AppProperties;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/v1/hr/demo/reset (HR only, demo mode only): wipes leave data and re-seeds demo requests.
 * Users and the append-only audit log are kept.
 */
@RestController
public class DemoResetService {
    private final AppProperties props;
    private final EntityManager em;
    private final DemoDataSeeder seeder;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public DemoResetService(AppProperties props, EntityManager em, DemoDataSeeder seeder, AuditService audit,
                            PlatformTransactionManager txManager) {
        this.props = props;
        this.em = em;
        this.seeder = seeder;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    @PostMapping("/api/v1/hr/demo/reset")
    public Map<String, Object> reset(@AuthenticationPrincipal CurrentUser me) {
        if (!props.demo().enabled()) {
            throw Errors.notFound("Not found");
        }
        tx.executeWithoutResult(s -> {
            for (String table : new String[]{"notification", "approval_action", "conflict_flag", "escalation",
                    "leave_request", "leave_balance", "assistant_interaction"}) {
                em.createNativeQuery("DELETE FROM " + table).executeUpdate();
            }
        });
        seeder.seedRequests();
        audit.recordIsolated(me, null, AuditAction.DEMO_RESET, "SYSTEM", null, "Demo leave data reset", null);
        return Map.of("ok", true);
    }
}
