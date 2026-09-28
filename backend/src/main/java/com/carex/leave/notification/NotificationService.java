package com.carex.leave.notification;

import com.carex.leave.common.time.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** In-app notifications written in the same transaction as the business change (assumption A14). */
@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final BusinessCalendar calendar;

    public NotificationService(NotificationRepository repo, BusinessCalendar calendar) {
        this.repo = repo;
        this.calendar = calendar;
    }

    @Transactional
    public void notify(Collection<Long> recipients, String type, Long requestId, String title, String body) {
        Set<Long> unique = new LinkedHashSet<>(recipients);
        unique.removeIf(Objects::isNull);
        for (Long r : unique) {
            repo.save(new Notification(r, type, requestId, title, body, calendar.now()));
        }
    }
}
