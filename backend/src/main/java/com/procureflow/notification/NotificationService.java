package com.procureflow.notification;

import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.security.SecurityUtils;
import com.procureflow.user.Role;
import com.procureflow.user.User;
import com.procureflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository users;

    public NotificationService(NotificationRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    @Transactional
    public void notifyUser(User user, NotificationType type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(truncate(title, 150));
        n.setMessage(truncate(message, 500));
        n.setLink(link);
        repository.save(n);
    }

    @Transactional
    public void notifyRole(Role role, NotificationType type, String title, String message, String link) {
        for (User user : users.findByRoleAndActiveTrue(role)) {
            notifyUser(user, type, title, message, link);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(boolean unreadOnly, int page, int size) {
        Long userId = SecurityUtils.current().id();
        var pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Notification> result = unreadOnly
                ? repository.findByUserIdAndReadFalse(userId, pageable)
                : repository.findByUserId(userId, pageable);
        return PageResponse.of(result, NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndReadFalse(SecurityUtils.current().id());
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> ApiException.notFound("Notification", id));
        if (!n.getUser().getId().equals(SecurityUtils.current().id())) {
            throw ApiException.forbidden("This notification belongs to another user");
        }
        n.setRead(true);
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead(SecurityUtils.current().id());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
