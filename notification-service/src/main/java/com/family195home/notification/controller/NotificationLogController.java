package com.family195home.notification.controller;

import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.dto.NotificationLogView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 通知發送紀錄查詢端點，供維運/測試檢視，非核心功能（見 contracts/notification-service.md）。 */
@RestController
public class NotificationLogController {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationLogController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @GetMapping("/api/notifications/logs")
    public ResponseEntity<List<NotificationLogView>> logs(
            @RequestParam Long familyGroupId, @RequestParam String month) {
        List<NotificationLogView> views = notificationLogRepository.findByGroupAndMonth(familyGroupId, month).stream()
                .map(log -> new NotificationLogView(
                        log.getFamilyMemberId(),
                        log.getLineUserId(),
                        log.getStatus().name(),
                        log.getAttempts(),
                        log.getLastAttemptAt() != null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(log.getLastAttemptAt()) : null,
                        log.getErrorMessage()))
                .toList();
        return ResponseEntity.ok(views);
    }
}
