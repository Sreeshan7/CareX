package com.carex.leave.workflow;

import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.config.AppProperties;
import com.carex.leave.leave.request.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistent automatic escalation (implementation.md §11). No in-memory state: deadlines live on the request row.
 * One transaction per request; rows are claimed with FOR UPDATE SKIP LOCKED so concurrent instances,
 * a double-fired job, or an in-flight approval never lead to double escalation.
 */
@Component
public class EscalationJob {
    private static final Logger log = LoggerFactory.getLogger(EscalationJob.class);

    private final LeaveRequestRepository requests;
    private final LeaveWorkflowService workflow;
    private final BusinessCalendar calendar;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public EscalationJob(LeaveRequestRepository requests, LeaveWorkflowService workflow, BusinessCalendar calendar,
                         AppProperties props, PlatformTransactionManager txManager) {
        this.requests = requests;
        this.workflow = workflow;
        this.calendar = calendar;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${app.escalation.poll-interval}", initialDelayString = "${app.escalation.initial-delay}")
    public void scheduledRun() {
        if (!props.escalation().enabled()) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("escalation.tick failed: {}", e.getMessage());
        }
    }

    /** Escalates every overdue stage (up to batch size). Returns how many requests were escalated. */
    public int runOnce() {
        long t0 = System.nanoTime();
        Instant now = calendar.now();
        Set<Long> attempted = new LinkedHashSet<>();
        int escalated = 0;
        for (int i = 0; i < props.escalation().batchSize(); i++) {
            try {
                Boolean done = tx.execute(status -> {
                    Optional<Long> id = requests.claimOverdue(now, arrayLiteral(attempted));
                    if (id.isEmpty()) {
                        return null;
                    }
                    attempted.add(id.get());
                    return workflow.escalateLocked(id.get(), now);
                });
                if (done == null) {
                    break;
                }
                if (done) {
                    escalated++;
                }
            } catch (RuntimeException e) {
                // e.g. uq_escalation_request_stage violated by a concurrent instance → already escalated; skip row
                log.warn("escalation of a request failed and was rolled back: {}", e.getMessage());
            }
        }
        log.info("escalation.tick claimed={} escalated={} ms={}", attempted.size(), escalated,
                (System.nanoTime() - t0) / 1_000_000);
        return escalated;
    }

    private static String arrayLiteral(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }
}
