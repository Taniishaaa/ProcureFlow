package com.procureflow.audit;

import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.security.SecurityUtils;
import com.procureflow.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Records who did what and when. Entries are written in the caller's transaction so they commit atomically. */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 2000;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        Optional<UserPrincipal> actor = SecurityUtils.currentOptional();
        Long actorId = actor.map(UserPrincipal::id).orElse(null);
        String actorEmail = actor.map(UserPrincipal::email).orElse("system");
        logAs(actorId, actorEmail, action, entityType, entityId, details);
    }

    @Transactional
    public void logAs(Long actorId, String actorEmail, String action, String entityType, Long entityId,
                      String details) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        if (details != null && details.length() > MAX_DETAILS) {
            entry.setDetails(details.substring(0, MAX_DETAILS));
        } else {
            entry.setDetails(details);
        }
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(String entityType, String action, String actor, LocalDate from,
                                            LocalDate to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(entityType)) {
            String value = entityType.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), value));
        }
        if (StringUtils.hasText(action)) {
            String value = action.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), value));
        }
        if (StringUtils.hasText(actor)) {
            String like = "%" + actor.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("actorEmail")), like));
        }
        if (from != null) {
            Instant start = Paging.startOf(from);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
        }
        if (to != null) {
            Instant end = Paging.endExclusive(to);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), end));
        }
        Page<AuditLog> result = repository.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result, AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> history(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId).stream()
                .map(AuditLogDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> recent(int count) {
        return repository.findAllByOrderByIdDesc(PageRequest.of(0, count)).stream().map(AuditLogDto::from).toList();
    }
}
