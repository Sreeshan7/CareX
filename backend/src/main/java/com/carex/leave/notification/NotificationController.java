package com.carex.leave.notification;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.Errors;
import com.carex.leave.common.time.BusinessCalendar;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository repo;
    private final BusinessCalendar calendar;

    public NotificationController(NotificationRepository repo, BusinessCalendar calendar) {
        this.repo = repo;
        this.calendar = calendar;
    }

    public record NotificationView(Long id, String type, Long requestId, String title, String body, Instant readAt,
                                   Instant createdAt) {}

    @GetMapping
    public List<NotificationView> list(@AuthenticationPrincipal CurrentUser me,
                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(defaultValue = "30") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        var rows = unreadOnly ? repo.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(me.id(), page)
                : repo.findByRecipientIdOrderByCreatedAtDescIdDesc(me.id(), page);
        return rows.stream().map(n -> new NotificationView(n.getId(), n.getType(), n.getRequestId(), n.getTitle(),
                n.getBody(), n.getReadAt(), n.getCreatedAt())).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CurrentUser me) {
        return Map.of("count", repo.countByRecipientIdAndReadAtIsNull(me.id()));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public Map<String, Boolean> markRead(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        Notification n = repo.findByIdAndRecipientId(id, me.id()).orElseThrow(() -> Errors.notFound("Not found"));
        n.markRead(calendar.now());
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal CurrentUser me) {
        return Map.of("updated", repo.markAllRead(me.id(), calendar.now()));
    }
}
