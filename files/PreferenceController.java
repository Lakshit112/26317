package com.notifications.controller;

import com.notifications.dto.NotificationDto;
import com.notifications.dto.PreferenceDto;
import com.notifications.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user notification preferences.
 *
 * Base path: /api/v1/notifications/users/{userId}/preferences
 */
@RestController
@RequestMapping("/api/v1/notifications/users/{userId}/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService service;

    /**
     * GET /api/v1/notifications/users/{userId}/preferences
     * Retrieve notification preferences for a user.
     */
    @GetMapping
    public ResponseEntity<NotificationDto.ApiResponse<PreferenceDto.PreferenceResponse>> get(
            @PathVariable String userId) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok(service.getPreferences(userId)));
    }

    /**
     * PATCH /api/v1/notifications/users/{userId}/preferences
     * Partially update notification preferences (only provided fields are changed).
     */
    @PatchMapping
    public ResponseEntity<NotificationDto.ApiResponse<PreferenceDto.PreferenceResponse>> update(
            @PathVariable String userId,
            @RequestBody PreferenceDto.UpdateRequest req) {
        return ResponseEntity.ok(
                NotificationDto.ApiResponse.ok("Preferences updated",
                        service.updatePreferences(userId, req)));
    }
}
