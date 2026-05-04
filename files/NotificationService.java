package com.notifications.service;

import com.notifications.dto.NotificationDto;
import com.notifications.exception.NotificationNotFoundException;
import com.notifications.model.Notification;
import com.notifications.repository.NotificationRepository;
import com.notifications.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final NotificationWebSocketHandler webSocketHandler;

    // ── Create ──────────────────────────────────────────────────────────────

    public NotificationDto.NotificationResponse create(NotificationDto.CreateRequest req) {
        Notification saved = repository.save(mapper.toEntity(req));
        NotificationDto.NotificationResponse response = mapper.toResponse(saved);
        // Push via WebSocket
        webSocketHandler.sendToUser(saved.getUserId(), response);
        log.info("Notification created id={} userId={}", saved.getId(), saved.getUserId());
        return response;
    }

    public NotificationDto.BulkOperationResponse createBulk(NotificationDto.BulkCreateRequest req) {
        List<String> failedIds = new ArrayList<>();
        int succeeded = 0;

        for (String userId : req.getUserIds()) {
            try {
                NotificationDto.CreateRequest single = NotificationDto.CreateRequest.builder()
                        .userId(userId)
                        .title(req.getTitle())
                        .message(req.getMessage())
                        .type(req.getType())
                        .priority(req.getPriority())
                        .actionUrl(req.getActionUrl())
                        .sourceService(req.getSourceService())
                        .expiresAt(req.getExpiresAt())
                        .build();
                create(single);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to create notification for userId={}", userId, e);
                failedIds.add(userId);
            }
        }

        return NotificationDto.BulkOperationResponse.builder()
                .requested(req.getUserIds().size())
                .succeeded(succeeded)
                .failed(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    // ── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationDto.NotificationResponse getById(String id, String userId) {
        Notification n = repository.findById(id)
                .filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return mapper.toResponse(n);
    }

    @Transactional(readOnly = true)
    public NotificationDto.PagedResponse<NotificationDto.NotificationResponse> getForUser(
            String userId,
            Boolean isRead,
            Notification.NotificationType type,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> resultPage;

        if (isRead != null && type != null) {
            resultPage = repository.findByUserIdAndIsReadAndTypeOrderByCreatedAtDesc(userId, isRead, type, pageable);
        } else if (isRead != null) {
            resultPage = repository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        } else if (type != null) {
            resultPage = repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
        } else {
            resultPage = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        List<NotificationDto.NotificationResponse> data = resultPage.getContent()
                .stream().map(mapper::toResponse).toList();

        return NotificationDto.PagedResponse.<NotificationDto.NotificationResponse>builder()
                .data(data)
                .page(page)
                .size(size)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .hasNext(resultPage.hasNext())
                .hasPrevious(resultPage.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public NotificationDto.UnreadCountResponse getUnreadCount(String userId) {
        long total = repository.countByUserIdAndIsRead(userId, false);
        long critical = repository.countByUserIdAndIsReadAndPriority(
                userId, false, Notification.NotificationPriority.CRITICAL);
        long high = repository.countByUserIdAndIsReadAndPriority(
                userId, false, Notification.NotificationPriority.HIGH);

        return NotificationDto.UnreadCountResponse.builder()
                .userId(userId)
                .totalUnread(total)
                .criticalUnread(critical)
                .highUnread(high)
                .build();
    }

    // ── Mark Read ───────────────────────────────────────────────────────────

    public NotificationDto.NotificationResponse markRead(String id, String userId) {
        Notification n = repository.findById(id)
                .filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(id));

        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(Instant.now());
            n = repository.save(n);
        }
        return mapper.toResponse(n);
    }

    public int markAllRead(String userId) {
        return repository.markAllReadByUserId(userId, Instant.now());
    }

    public NotificationDto.BulkOperationResponse markBulkRead(
            String userId, NotificationDto.BulkMarkReadRequest req) {

        int updated = repository.markReadByIds(req.getNotificationIds(), userId, Instant.now());
        int failed = req.getNotificationIds().size() - updated;

        return NotificationDto.BulkOperationResponse.builder()
                .requested(req.getNotificationIds().size())
                .succeeded(updated)
                .failed(failed)
                .failedIds(List.of())
                .build();
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    public void delete(String id, String userId) {
        Notification n = repository.findById(id)
                .filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(id));
        repository.delete(n);
        log.info("Notification deleted id={} userId={}", id, userId);
    }

    // ── Scheduled cleanup ───────────────────────────────────────────────────

    @Scheduled(cron = "0 0 2 * * *") // Every day at 2am
    public void purgeExpired() {
        int deleted = repository.deleteExpiredNotifications(Instant.now());
        log.info("Purged {} expired notifications", deleted);
    }
}
