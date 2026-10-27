package com.procureflow.notification;

import java.time.Instant;

public record NotificationDto(Long id, NotificationType type, String title, String message, String link,
                              boolean read, Instant createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(),
                n.getCreatedAt());
    }
}
