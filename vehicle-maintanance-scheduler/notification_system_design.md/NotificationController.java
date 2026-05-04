package com.notifications.controller;

import com.notifications.dto.NotificationDto;
import com.notifications.model.Notification;
import com.notifications.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for notification CRUD and bulk operations.
 *
 * Base path: /api/v1/notifications
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /**
     * POST /api/v1/notifications
     * Create a single notification.
     */
    @PostMapping
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.NotificationResponse>> create(
            @Valid @RequestBody NotificationDto.CreateRequest req) {
        NotificationDto.NotificationResponse created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationDto.ApiResponse.ok("Notification created", created));
    }

    /**
     * POST /api/v1/notifications/bulk
     * Broadcast a notification to multiple users.
     */
    @PostMapping("/bulk")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.BulkOperationResponse>> createBulk(
            @Valid @RequestBody NotificationDto.BulkCreateRequest req) {
        NotificationDto.BulkOperationResponse result = service.createBulk(req);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                .body(NotificationDto.ApiResponse.ok("Bulk create complete", result));
    }

    /**
     * GET /api/v1/notifications/users/{userId}
     * List notifications for a user with optional filters and pagination.
     *
     * Query params:
     *   isRead   - true/false
     *   type     - SYSTEM | ALERT | PROMOTION | SOCIAL | TRANSACTION | REMINDER
     *   page     - 0-based (default 0)
     *   size     - page size (default 20)
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.PagedResponse<NotificationDto.NotificationResponse>>> list(
            @PathVariable String userId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Notification.NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100); // cap
        NotificationDto.PagedResponse<NotificationDto.NotificationResponse> paged =
                service.getForUser(userId, isRead, type, page, size);
        return ResponseEntity.ok(NotificationDto.ApiResponse.ok(paged));
    }

    /**
     * GET /api/v1/notifications/users/{userId}/unread-count
     * Get unread notification counts, broken down by priority.
     */
    @GetMapping("/users/{userId}/unread-count")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.UnreadCountResponse>> unreadCount(
            @PathVariable String userId) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok(service.getUnreadCount(userId)));
    }

    /**
     * GET /api/v1/notifications/{id}
     * Fetch a single notification by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.NotificationResponse>> getById(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok(service.getById(id, userId)));
    }

    /**
     * PATCH /api/v1/notifications/{id}/read
     * Mark a single notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.NotificationResponse>> markRead(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok("Notification marked as read",
                        service.markRead(id, userId)));
    }

    /**
     * PATCH /api/v1/notifications/users/{userId}/read-all
     * Mark all notifications as read for a user.
     */
    @PatchMapping("/users/{userId}/read-all")
    public ResponseEntity<NotificationDto.ApiResponse<Void>> markAllRead(
            @PathVariable String userId) {
        int count = service.markAllRead(userId);
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok(count + " notifications marked as read", null));
    }

    /**
     * PATCH /api/v1/notifications/bulk/read
     * Mark specific notifications as read.
     */
    @PatchMapping("/bulk/read")
    public ResponseEntity<NotificationDto.ApiResponse<NotificationDto.BulkOperationResponse>> markBulkRead(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody NotificationDto.BulkMarkReadRequest req) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok("Bulk read complete",
                        service.markBulkRead(userId, req)));
    }

    /**
     * DELETE /api/v1/notifications/{id}
     * Delete a notification.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<NotificationDto.ApiResponse<Void>> delete(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        service.delete(id, userId);
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok("Notification deleted", null));
    }
}
