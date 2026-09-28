package com.carex.leave.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    List<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    List<Notification> findByRequestId(Long requestId);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.recipientId = :me and n.readAt is null")
    int markAllRead(@Param("me") Long me, @Param("at") Instant at);
}
