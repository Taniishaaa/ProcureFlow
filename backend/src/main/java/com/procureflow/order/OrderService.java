package com.procureflow.order;

import com.procureflow.audit.AuditLogDto;
import com.procureflow.audit.AuditService;
import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.notification.NotificationService;
import com.procureflow.notification.NotificationType;
import com.procureflow.order.OrderDtos.CreateOrderRequest;
import com.procureflow.order.OrderDtos.DeliveryDto;
import com.procureflow.order.OrderDtos.DeliveryInput;
import com.procureflow.order.OrderDtos.DeliveryLineDto;
import com.procureflow.order.OrderDtos.DeliveryLineInput;
import com.procureflow.order.OrderDtos.OrderDetail;
import com.procureflow.order.OrderDtos.OrderItemDto;
import com.procureflow.order.OrderDtos.OrderSummary;
import com.procureflow.order.OrderDtos.RequestRef;
import com.procureflow.request.PurchaseRequest;
import com.procureflow.request.PurchaseRequestItem;
import com.procureflow.request.PurchaseRequestRepository;
import com.procureflow.request.RequestStatus;
import com.procureflow.security.SecurityUtils;
import com.procureflow.security.UserPrincipal;
import com.procureflow.settings.SettingsService;
import com.procureflow.supplier.Quotation;
import com.procureflow.supplier.QuotationLine;
import com.procureflow.supplier.QuotationRepository;
import com.procureflow.supplier.QuotationStatus;
import com.procureflow.supplier.Supplier;
import com.procureflow.supplier.SupplierDtos.SupplierDto;
import com.procureflow.supplier.SupplierDtos.SupplierRef;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Purchase order lifecycle: create from an approved request and a quotation, issue, receive deliveries, cancel.
 * The backend re-validates every rule (approved request, open quotation, quantities) regardless of what the
 * client sends, and serialises changes to one order or request with row locks.
 */
@Service
public class OrderService {

    static final String ENTITY = "PURCHASE_ORDER";

    private final PurchaseOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final PurchaseRequestRepository requests;
    private final QuotationRepository quotations;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SettingsService settings;

    public OrderService(PurchaseOrderRepository orders, DeliveryRepository deliveries,
                        PurchaseRequestRepository requests, QuotationRepository quotations, UserRepository users,
                        AuditService audit, NotificationService notifications, SettingsService settings) {
        this.orders = orders;
        this.deliveries = deliveries;
        this.requests = requests;
        this.quotations = quotations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.settings = settings;
    }

    // =====================================================================================================
    // Commands
    // =====================================================================================================

    @Transactional
    public OrderDetail create(CreateOrderRequest in) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseRequest r = requests.findByIdForUpdate(in.requestId())
                .orElseThrow(() -> ApiException.notFound("Request", in.requestId()));
        if (r.getStatus() != RequestStatus.APPROVED) {
            throw ApiException.conflict("Purchase orders can only be created from approved requests "
                    + "(this request is " + r.getStatus() + ")");
        }
        Quotation q = quotations.findById(in.quotationId())
                .orElseThrow(() -> ApiException.notFound("Quotation", in.quotationId()));
        if (!q.getRequest().getId().equals(r.getId())) {
            throw ApiException.badRequest("The quotation does not belong to this request");
        }
        if (q.getStatus() != QuotationStatus.SUBMITTED) {
            throw ApiException.conflict("Only open quotations can be ordered (this one is " + q.getStatus() + ")");
        }
        if (q.isExpired()) {
            throw ApiException.badRequest("The quotation has expired. Ask the supplier for an updated offer.");
        }
        Supplier supplier = q.getSupplier();
        if (!supplier.isActive()) {
            throw ApiException.badRequest("Supplier '" + supplier.getName() + "' is inactive");
        }
        User creator = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User", p.id()));

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("TMP-" + UUID.randomUUID());
        po.setRequest(r);
        po.setSupplier(supplier);
        po.setQuotation(q);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setSubtotal(q.getSubtotal());
        po.setTaxPercent(q.getTaxPercent());
        po.setTaxAmount(q.getTaxAmount());
        po.setTotalAmount(q.getTotalAmount());
        po.setDeliveryDays(q.getDeliveryDays());
        po.setPaymentTerms(q.getPaymentTerms());
        po.setNotes(StringUtils.hasText(in.notes()) ? in.notes().trim() : null);
        po.setCreatedBy(creator);
        for (QuotationLine line : q.getLines()) {
            PurchaseRequestItem requested = line.getRequestItem();
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setName(requested.getName());
            item.setDescription(requested.getDescription());
            item.setQuantityOrdered(requested.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            po.addItem(item);
        }
        po = orders.saveAndFlush(po);
        po.setPoNumber(String.format("%s-%d-%06d", settings.get(SettingsService.PO_PREFIX), Year.now().getValue(),
                po.getId()));

        for (Quotation other : quotations.findByRequestId(r.getId())) {
            other.setStatus(other.getId().equals(q.getId()) ? QuotationStatus.SELECTED : QuotationStatus.REJECTED);
        }
        r.setStatus(RequestStatus.ORDERED);

        audit.log("CREATE", ENTITY, po.getId(), "Created " + po.getPoNumber() + " for " + r.getRequestNumber()
                + " from '" + supplier.getName() + "' (total " + po.getTotalAmount().toPlainString() + ")");
        return toDetail(po, p);
    }

    @Transactional
    public OrderDetail issue(Long id) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw ApiException.conflict("Only draft orders can be issued (current status: " + po.getStatus() + ")");
        }
        if (!po.getSupplier().isActive()) {
            throw ApiException.badRequest("Supplier '" + po.getSupplier().getName() + "' is inactive");
        }
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setIssuedAt(Instant.now());
        po.setExpectedDeliveryDate(LocalDate.now().plusDays(po.getDeliveryDays()));
        audit.log("ISSUE", ENTITY, po.getId(), "Issued " + po.getPoNumber() + " to '" + po.getSupplier().getName()
                + "', expected delivery " + po.getExpectedDeliveryDate());
        PurchaseRequest r = po.getRequest();
        notifications.notifyUser(r.getRequester(), NotificationType.ORDER, "Purchase order issued",
                po.getPoNumber() + " was issued to " + po.getSupplier().getName() + " for your request "
                        + r.getRequestNumber() + ". Expected delivery: " + po.getExpectedDeliveryDate() + ".",
                "/orders/" + po.getId());
        return toDetail(po, p);
    }

    @Transactional
    public OrderDetail cancel(Long id, String reason) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.ISSUED) {
            throw ApiException.conflict(
                    "Only orders that have not received any goods can be cancelled (current status: "
                            + po.getStatus() + ")");
        }
        boolean wasIssued = po.getStatus() == PurchaseOrderStatus.ISSUED;
        PurchaseRequest r = requests.findByIdForUpdate(po.getRequest().getId())
                .orElseThrow(() -> ApiException.notFound("Request", po.getRequest().getId()));
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        // The request goes back to the procurement queue and every offer becomes available again.
        r.setStatus(RequestStatus.APPROVED);
        for (Quotation quotation : quotations.findByRequestId(r.getId())) {
            quotation.setStatus(QuotationStatus.SUBMITTED);
        }
        audit.log("CANCEL", ENTITY, po.getId(), "Cancelled " + po.getPoNumber() + ": " + reason.trim());
        if (wasIssued) {
            notifications.notifyUser(r.getRequester(), NotificationType.ORDER, "Purchase order cancelled",
                    po.getPoNumber() + " was cancelled: " + reason.trim(), "/requests/" + r.getId());
        }
        return toDetail(po, p);
    }

    @Transactional
    public OrderDetail recordDelivery(Long id, DeliveryInput in) {
        UserPrincipal p = SecurityUtils.current();
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        if (po.getStatus() != PurchaseOrderStatus.ISSUED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw ApiException.conflict("Deliveries can only be recorded against issued orders "
                    + "(current status: " + po.getStatus() + ")");
        }
        Map<Long, PurchaseOrderItem> items = new HashMap<>();
        for (PurchaseOrderItem item : po.getItems()) {
            items.put(item.getId(), item);
        }
        User receiver = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User", p.id()));

        Delivery delivery = new Delivery();
        delivery.setPurchaseOrder(po);
        delivery.setReceivedDate(in.receivedDate());
        delivery.setNotes(StringUtils.hasText(in.notes()) ? in.notes().trim() : null);
        delivery.setReceivedBy(receiver);

        Set<Long> seen = new HashSet<>();
        int totalUnits = 0;
        for (DeliveryLineInput line : in.lines()) {
            PurchaseOrderItem item = items.get(line.poItemId());
            if (item == null) {
                throw ApiException.badRequest("Item " + line.poItemId() + " is not part of this order");
            }
            if (!seen.add(line.poItemId())) {
                throw ApiException.badRequest("Item '" + item.getName() + "' appears more than once");
            }
            if (line.quantity() > item.outstanding()) {
                throw ApiException.badRequest("Cannot receive " + line.quantity() + " of '" + item.getName()
                        + "': only " + item.outstanding() + " still outstanding");
            }
            item.setQuantityReceived(item.getQuantityReceived() + line.quantity());
            DeliveryLine deliveryLine = new DeliveryLine();
            deliveryLine.setPoItem(item);
            deliveryLine.setQuantity(line.quantity());
            delivery.addLine(deliveryLine);
            totalUnits += line.quantity();
        }
        deliveries.save(delivery);

        boolean complete = po.getItems().stream().allMatch(i -> i.outstanding() == 0);
        po.setStatus(complete ? PurchaseOrderStatus.COMPLETED : PurchaseOrderStatus.PARTIALLY_RECEIVED);

        PurchaseRequest r = po.getRequest();
        if (complete) {
            PurchaseRequest locked = requests.findByIdForUpdate(r.getId())
                    .orElseThrow(() -> ApiException.notFound("Request", r.getId()));
            locked.setStatus(RequestStatus.FULFILLED);
        }
        audit.log("RECEIVE_DELIVERY", ENTITY, po.getId(), "Received " + totalUnits + " unit(s) on "
                + in.receivedDate() + " against " + po.getPoNumber() + "; order is now " + po.getStatus());
        notifications.notifyUser(r.getRequester(), NotificationType.DELIVERY,
                complete ? "Order fully delivered" : "Partial delivery received",
                po.getPoNumber() + " (" + r.getRequestNumber() + "): " + po.progressPercent() + "% received"
                        + (complete ? " - the order is complete." : "."),
                "/orders/" + po.getId());
        return toDetail(po, p);
    }

    // =====================================================================================================
    // Queries
    // =====================================================================================================

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(PurchaseOrderStatus status, Long supplierId, String search,
                                           LocalDate from, LocalDate to, int page, int size) {
        UserPrincipal p = SecurityUtils.current();
        Specification<PurchaseOrder> spec = (root, query, cb) -> cb.conjunction();
        if (p.role() == Role.EMPLOYEE) {
            // Employees only see orders for their own requests, and only once the order has been issued.
            spec = spec.and((root, query, cb) -> cb.equal(root.get("request").get("requester").get("id"), p.id()));
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), PurchaseOrderStatus.DRAFT));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (supplierId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        }
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                Join<PurchaseOrder, Supplier> supplier = root.join("supplier", JoinType.LEFT);
                Join<PurchaseOrder, PurchaseRequest> request = root.join("request", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("poNumber")), like),
                        cb.like(cb.lower(supplier.get("name")), like),
                        cb.like(cb.lower(request.get("requestNumber")), like),
                        cb.like(cb.lower(request.get("title")), like));
            });
        }
        if (from != null) {
            Instant start = Paging.startOf(from);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
        }
        if (to != null) {
            Instant end = Paging.endExclusive(to);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), end));
        }
        Page<PurchaseOrder> result = orders.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        UserPrincipal p = SecurityUtils.current();
        return toDetail(loadAccessible(id, p), p);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> history(Long id) {
        UserPrincipal p = SecurityUtils.current();
        loadAccessible(id, p);
        return audit.history(ENTITY, id);
    }

    // =====================================================================================================
    // Mapping
    // =====================================================================================================

    public OrderSummary toSummary(PurchaseOrder po) {
        PurchaseRequest r = po.getRequest();
        return new OrderSummary(po.getId(), po.getPoNumber(), po.getStatus(), r.getId(), r.getRequestNumber(),
                r.getTitle(), r.getRequester().getFullName(), SupplierRef.from(po.getSupplier()),
                po.getTotalAmount(), po.getIssuedAt(), po.getExpectedDeliveryDate(), po.getCreatedAt(),
                po.progressPercent());
    }

    private OrderDetail toDetail(PurchaseOrder po, UserPrincipal p) {
        PurchaseRequest r = po.getRequest();
        List<OrderItemDto> items = po.getItems().stream()
                .map(i -> new OrderItemDto(i.getId(), i.getName(), i.getDescription(), i.getQuantityOrdered(),
                        i.getQuantityReceived(), i.outstanding(), i.getUnitPrice(), i.lineTotal()))
                .toList();
        List<DeliveryDto> deliveryList = deliveries.findByPurchaseOrderIdOrderByReceivedDateDescIdDesc(po.getId())
                .stream()
                .map(d -> new DeliveryDto(d.getId(), d.getReceivedDate(), d.getNotes(),
                        d.getReceivedBy().getFullName(), d.getCreatedAt(),
                        d.getLines().stream()
                                .map(l -> new DeliveryLineDto(l.getPoItem().getId(), l.getPoItem().getName(),
                                        l.getQuantity()))
                                .toList()))
                .toList();
        RequestRef requestRef = new RequestRef(r.getId(), r.getRequestNumber(), r.getTitle(),
                r.getRequester().getId(), r.getRequester().getFullName());
        return new OrderDetail(po.getId(), po.getPoNumber(), po.getStatus(), requestRef,
                SupplierDto.from(po.getSupplier()), po.getSubtotal(), po.getTaxPercent(), po.getTaxAmount(),
                po.getTotalAmount(), po.getDeliveryDays(), po.getPaymentTerms(), po.getCreatedAt(),
                po.getIssuedAt(), po.getExpectedDeliveryDate(), po.getNotes(), po.getCreatedBy().getFullName(),
                items, deliveryList, po.progressPercent(), allowedActions(po, p));
    }

    private Set<String> allowedActions(PurchaseOrder po, UserPrincipal p) {
        Set<String> actions = new LinkedHashSet<>();
        if (p.role() == Role.PROCUREMENT_OFFICER) {
            switch (po.getStatus()) {
                case DRAFT -> {
                    actions.add("ISSUE");
                    actions.add("CANCEL");
                }
                case ISSUED -> {
                    actions.add("RECEIVE");
                    actions.add("CANCEL");
                }
                case PARTIALLY_RECEIVED -> actions.add("RECEIVE");
                default -> {
                }
            }
        }
        return actions;
    }

    private PurchaseOrder loadAccessible(Long id, UserPrincipal p) {
        PurchaseOrder po = orders.findById(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        if (p.role() == Role.MANAGER) {
            throw ApiException.forbidden("You do not have access to purchase orders");
        }
        if (p.role() == Role.EMPLOYEE) {
            boolean owner = po.getRequest().getRequester().getId().equals(p.id());
            if (!owner || po.getStatus() == PurchaseOrderStatus.DRAFT) {
                throw ApiException.forbidden("You do not have access to this order");
            }
        }
        return po;
    }
}
