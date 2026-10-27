package com.procureflow.request;

import com.procureflow.audit.AuditService;
import com.procureflow.category.Category;
import com.procureflow.category.CategoryRepository;
import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.common.Pricing;
import com.procureflow.common.Refs.CategoryRef;
import com.procureflow.common.Refs.UserRef;
import com.procureflow.notification.NotificationService;
import com.procureflow.notification.NotificationType;
import com.procureflow.order.PurchaseOrder;
import com.procureflow.order.PurchaseOrderRepository;
import com.procureflow.order.PurchaseOrderStatus;
import com.procureflow.request.RequestDtos.ApprovalDto;
import com.procureflow.request.RequestDtos.ApprovalRecord;
import com.procureflow.request.RequestDtos.ItemDto;
import com.procureflow.request.RequestDtos.ItemInput;
import com.procureflow.request.RequestDtos.RequestDetail;
import com.procureflow.request.RequestDtos.RequestSummary;
import com.procureflow.request.RequestDtos.SaveRequest;
import com.procureflow.security.SecurityUtils;
import com.procureflow.security.UserPrincipal;
import com.procureflow.settings.SettingsService;
import com.procureflow.supplier.QuotationRepository;
import com.procureflow.user.Role;
import com.procureflow.user.User;
import com.procureflow.user.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Purchase request lifecycle: draft -> submitted -> approved/rejected, plus listing and access rules.
 * State transitions take a row lock and re-check the current status, so the rules hold even when two users
 * (or a direct API caller) act on the same request at the same time.
 */
@Service
public class RequestService {

    private static final Set<RequestStatus> PROCUREMENT_VISIBLE =
            EnumSet.of(RequestStatus.APPROVED, RequestStatus.ORDERED, RequestStatus.FULFILLED);

    private final PurchaseRequestRepository requests;
    private final ApprovalRepository approvals;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final QuotationRepository quotations;
    private final PurchaseOrderRepository orders;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SettingsService settings;

    public RequestService(PurchaseRequestRepository requests, ApprovalRepository approvals, UserRepository users,
                          CategoryRepository categories, QuotationRepository quotations,
                          PurchaseOrderRepository orders, AuditService audit, NotificationService notifications,
                          SettingsService settings) {
        this.requests = requests;
        this.approvals = approvals;
        this.users = users;
        this.categories = categories;
        this.quotations = quotations;
        this.orders = orders;
        this.audit = audit;
        this.notifications = notifications;
        this.settings = settings;
    }

    // =====================================================================================================
    // Commands
    // =====================================================================================================

    @Transactional
    public RequestDetail create(SaveRequest dto) {
        UserPrincipal p = SecurityUtils.current();
        User requester = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User", p.id()));
        PurchaseRequest r = new PurchaseRequest();
        r.setRequestNumber("TMP-" + UUID.randomUUID());
        r.setRequester(requester);
        r.setStatus(RequestStatus.DRAFT);
        apply(r, dto);
        r = requests.saveAndFlush(r);
        r.setRequestNumber(String.format("PR-%d-%06d", Year.now().getValue(), r.getId()));
        audit.log("CREATE", "PURCHASE_REQUEST", r.getId(),
                "Created request " + r.getRequestNumber() + " '" + r.getTitle() + "'");
        if (Boolean.TRUE.equals(dto.submit())) {
            submitInternal(r, p);
        }
        return toDetail(r, p);
    }

    @Transactional
    public RequestDetail update(Long id, SaveRequest dto) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = loadOwnedForUpdate(id, p);
        if (r.getStatus() != RequestStatus.DRAFT && r.getStatus() != RequestStatus.REJECTED) {
            throw ApiException.conflict(
                    "Only draft or rejected requests can be edited (current status: " + r.getStatus() + ")");
        }
        boolean wasRejected = r.getStatus() == RequestStatus.REJECTED;
        apply(r, dto);
        if (wasRejected) {
            r.setStatus(RequestStatus.DRAFT);
        }
        requests.flush();
        audit.log("UPDATE", "PURCHASE_REQUEST", r.getId(), "Edited request " + r.getRequestNumber());
        if (Boolean.TRUE.equals(dto.submit())) {
            submitInternal(r, p);
        }
        return toDetail(r, p);
    }

    @Transactional
    public RequestDetail submit(Long id) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = loadOwnedForUpdate(id, p);
        submitInternal(r, p);
        return toDetail(r, p);
    }

    @Transactional
    public RequestDetail cancel(Long id) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = loadOwnedForUpdate(id, p);
        if (r.getStatus() != RequestStatus.DRAFT && r.getStatus() != RequestStatus.SUBMITTED) {
            throw ApiException.conflict(
                    "Only draft or submitted requests can be cancelled (current status: " + r.getStatus() + ")");
        }
        r.setStatus(RequestStatus.CANCELLED);
        audit.log("CANCEL", "PURCHASE_REQUEST", r.getId(), "Cancelled request " + r.getRequestNumber());
        return toDetail(r, p);
    }

    @Transactional
    public RequestDetail approve(Long id, String comments) {
        return decide(id, ApprovalDecision.APPROVED, comments);
    }

    @Transactional
    public RequestDetail reject(Long id, String comments) {
        return decide(id, ApprovalDecision.REJECTED, comments);
    }

    private RequestDetail decide(Long id, ApprovalDecision decision, String comments) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = requests.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Request", id));
        if (r.getStatus() != RequestStatus.SUBMITTED) {
            throw ApiException.conflict(
                    "This request is not awaiting approval (current status: " + r.getStatus() + ")");
        }
        if (r.getRequester().getId().equals(p.id())) {
            throw ApiException.forbidden("You cannot decide on your own request");
        }
        String trimmed = StringUtils.hasText(comments) ? comments.trim() : null;
        if (decision == ApprovalDecision.REJECTED && trimmed == null) {
            throw ApiException.badRequest("A reason is required when rejecting a request");
        }
        User approver = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User", p.id()));

        Approval approval = new Approval();
        approval.setRequest(r);
        approval.setApprover(approver);
        approval.setDecision(decision);
        approval.setComments(trimmed);
        approval.setDecidedAt(Instant.now());
        approvals.save(approval);

        boolean approved = decision == ApprovalDecision.APPROVED;
        r.setStatus(approved ? RequestStatus.APPROVED : RequestStatus.REJECTED);

        String verb = approved ? "approved" : "rejected";
        String message = r.getRequestNumber() + " '" + r.getTitle() + "' was " + verb + " by " + p.fullName()
                + (trimmed != null ? ": " + trimmed : ".");
        notifications.notifyUser(r.getRequester(), NotificationType.APPROVAL, "Request " + verb, message,
                "/requests/" + r.getId());
        if (approved) {
            notifications.notifyRole(Role.PROCUREMENT_OFFICER, NotificationType.REQUEST,
                    "Approved request ready for procurement", r.getRequestNumber() + ": " + r.getTitle(),
                    "/requests/" + r.getId());
        }
        audit.log(approved ? "APPROVE" : "REJECT", "PURCHASE_REQUEST", r.getId(),
                r.getRequestNumber() + " " + verb + (trimmed != null ? " - " + trimmed : ""));
        return toDetail(r, p);
    }

    // =====================================================================================================
    // Queries
    // =====================================================================================================

    @Transactional(readOnly = true)
    public RequestDetail get(Long id) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Request", id));
        checkReadAccess(r, p);
        return toDetail(r, p);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> listMine(RequestStatus status, String search, int page, int size) {
        UserPrincipal p = SecurityUtils.current();
        Specification<PurchaseRequest> spec = base(status, search, null)
                .and((root, query, cb) -> cb.equal(root.get("requester").get("id"), p.id()));
        Page<PurchaseRequest> result = requests.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> listPendingApproval(String search, int page, int size) {
        UserPrincipal p = SecurityUtils.current();
        Specification<PurchaseRequest> spec = base(RequestStatus.SUBMITTED, search, null)
                .and((root, query, cb) -> cb.notEqual(root.get("requester").get("id"), p.id()));
        Page<PurchaseRequest> result = requests.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.ASC, "submittedAt", "id")));
        return PageResponse.of(result, this::toSummary);
    }

    /** Approved requests waiting for procurement, with the number of quotations recorded so far. */
    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> listApproved(String search, Long categoryId, int page, int size) {
        Specification<PurchaseRequest> spec = base(RequestStatus.APPROVED, search, categoryId);
        Page<PurchaseRequest> result = requests.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.ASC, "submittedAt", "id")));
        Map<Long, Long> counts = new HashMap<>();
        if (!result.isEmpty()) {
            List<Long> ids = result.getContent().stream().map(PurchaseRequest::getId).toList();
            for (Object[] row : quotations.countByRequestIds(ids)) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.of(result, r -> toSummaryWithCount(r, counts.getOrDefault(r.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> listAll(RequestStatus status, String search, Long categoryId, int page,
                                                int size) {
        Page<PurchaseRequest> result = requests.findAll(base(status, search, categoryId),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result, this::toSummary);
    }

    /** Past approval decisions. Managers see their own; administrators see everyone's. */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRecord> listDecisions(ApprovalDecision decision, String search, int page, int size) {
        UserPrincipal p = SecurityUtils.current();
        Specification<Approval> spec = (root, query, cb) -> cb.conjunction();
        if (p.role() == Role.MANAGER) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("approver").get("id"), p.id()));
        }
        if (decision != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("decision"), decision));
        }
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                Join<Approval, PurchaseRequest> req = root.join("request");
                return cb.or(
                        cb.like(cb.lower(req.get("title")), like),
                        cb.like(cb.lower(req.get("requestNumber")), like),
                        cb.like(cb.lower(root.get("comments")), like));
            });
        }
        Page<Approval> result = approvals.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "decidedAt", "id")));
        return PageResponse.of(result, a -> new ApprovalRecord(a.getId(), a.getRequest().getId(),
                a.getRequest().getRequestNumber(), a.getRequest().getTitle(),
                a.getRequest().getRequester().getFullName(), a.getRequest().getTotalEstimate(), a.getDecision(),
                a.getComments(), a.getDecidedAt(), a.getApprover().getFullName()));
    }

    // =====================================================================================================
    // Mapping (public so dashboards can reuse it inside their own transaction)
    // =====================================================================================================

    public RequestSummary toSummary(PurchaseRequest r) {
        return toSummaryWithCount(r, null);
    }

    private RequestSummary toSummaryWithCount(PurchaseRequest r, Long quotationCount) {
        return new RequestSummary(r.getId(), r.getRequestNumber(), r.getTitle(), r.getStatus(),
                CategoryRef.from(r.getCategory()), UserRef.from(r.getRequester()), r.getTotalEstimate(),
                r.getItems().size(), r.getSubmittedAt(), r.getCreatedAt(), quotationCount);
    }

    private RequestDetail toDetail(PurchaseRequest r, UserPrincipal p) {
        List<ItemDto> items = r.getItems().stream()
                .map(i -> new ItemDto(i.getId(), i.getName(), i.getDescription(), i.getQuantity(),
                        i.getEstimatedUnitPrice(), i.lineTotal()))
                .toList();
        List<ApprovalDto> decisions = approvals.findByRequestIdOrderByDecidedAtAscIdAsc(r.getId()).stream()
                .map(a -> new ApprovalDto(a.getId(), a.getDecision(), a.getComments(), a.getDecidedAt(),
                        UserRef.from(a.getApprover())))
                .toList();
        PurchaseOrder po = orders
                .findFirstByRequestIdAndStatusNotOrderByIdDesc(r.getId(), PurchaseOrderStatus.CANCELLED)
                .orElse(null);
        return new RequestDetail(r.getId(), r.getRequestNumber(), r.getTitle(), r.getStatus(),
                CategoryRef.from(r.getCategory()), UserRef.from(r.getRequester()), r.getJustification(),
                r.getTotalEstimate(), r.getSubmittedAt(), r.getCreatedAt(), r.getUpdatedAt(), items, decisions,
                po == null ? null : po.getId(), po == null ? null : po.getPoNumber(),
                po == null ? null : po.getStatus().name(), allowedActions(r, p));
    }

    // =====================================================================================================
    // Rules
    // =====================================================================================================

    private void apply(PurchaseRequest r, SaveRequest dto) {
        Category category = categories.findById(dto.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));
        if (!category.isActive()) {
            throw ApiException.badRequest("Category '" + category.getName() + "' is inactive");
        }
        r.setTitle(dto.title().trim());
        r.setCategory(category);
        r.setJustification(dto.justification().trim());
        r.clearItems();
        for (ItemInput in : dto.items()) {
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setName(in.name().trim());
            item.setDescription(StringUtils.hasText(in.description()) ? in.description().trim() : null);
            item.setQuantity(in.quantity());
            item.setEstimatedUnitPrice(Pricing.money(in.estimatedUnitPrice()));
            r.addItem(item);
        }
        r.recalcTotal();
    }

    private void submitInternal(PurchaseRequest r, UserPrincipal p) {
        if (r.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.conflict(
                    "Only draft requests can be submitted (current status: " + r.getStatus() + ")");
        }
        if (r.getItems().isEmpty()) {
            throw ApiException.badRequest("A request needs at least one item");
        }
        BigDecimal max = settings.getDecimal(SettingsService.REQUEST_MAX_TOTAL);
        if (r.getTotalEstimate().compareTo(max) > 0) {
            throw ApiException.badRequest(
                    "The estimated total exceeds the configured limit of " + max.toPlainString());
        }
        r.setStatus(RequestStatus.SUBMITTED);
        r.setSubmittedAt(Instant.now());
        audit.log("SUBMIT", "PURCHASE_REQUEST", r.getId(), "Submitted request " + r.getRequestNumber()
                + " (total " + r.getTotalEstimate().toPlainString() + ")");
        notifications.notifyRole(Role.MANAGER, NotificationType.APPROVAL, "Approval needed",
                p.fullName() + " submitted " + r.getRequestNumber() + ": " + r.getTitle(),
                "/approvals/" + r.getId());
    }

    private PurchaseRequest loadOwnedForUpdate(Long id, UserPrincipal p) {
        PurchaseRequest r = requests.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Request", id));
        if (!r.getRequester().getId().equals(p.id())) {
            throw ApiException.forbidden("You can only modify your own requests");
        }
        return r;
    }

    private void checkReadAccess(PurchaseRequest r, UserPrincipal p) {
        boolean owner = r.getRequester().getId().equals(p.id());
        boolean allowed = switch (p.role()) {
            case ADMIN -> true;
            case EMPLOYEE -> owner;
            case MANAGER -> owner || r.getStatus() != RequestStatus.DRAFT;
            case PROCUREMENT_OFFICER -> PROCUREMENT_VISIBLE.contains(r.getStatus());
        };
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this request");
        }
    }

    private Set<String> allowedActions(PurchaseRequest r, UserPrincipal p) {
        Set<String> actions = new LinkedHashSet<>();
        boolean owner = r.getRequester().getId().equals(p.id());
        if (p.role() == Role.EMPLOYEE && owner) {
            switch (r.getStatus()) {
                case DRAFT -> {
                    actions.add("EDIT");
                    actions.add("SUBMIT");
                    actions.add("CANCEL");
                }
                case REJECTED -> actions.add("EDIT");
                case SUBMITTED -> actions.add("CANCEL");
                default -> {
                }
            }
        }
        if (p.role() == Role.MANAGER && r.getStatus() == RequestStatus.SUBMITTED && !owner) {
            actions.add("APPROVE");
            actions.add("REJECT");
        }
        if (p.role() == Role.PROCUREMENT_OFFICER && r.getStatus() == RequestStatus.APPROVED) {
            actions.add("ADD_QUOTATION");
            actions.add("COMPARE_QUOTATIONS");
            actions.add("CREATE_PO");
        }
        return actions;
    }

    private Specification<PurchaseRequest> base(RequestStatus status, String search, Long categoryId) {
        Specification<PurchaseRequest> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("requestNumber")), like),
                    cb.like(cb.lower(root.join("requester", JoinType.LEFT).get("fullName")), like)));
        }
        return spec;
    }
}
