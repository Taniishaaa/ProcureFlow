package com.procureflow.supplier;

import com.procureflow.audit.AuditService;
import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.common.Pricing;
import com.procureflow.request.PurchaseRequest;
import com.procureflow.request.PurchaseRequestItem;
import com.procureflow.request.PurchaseRequestRepository;
import com.procureflow.request.RequestStatus;
import com.procureflow.security.SecurityUtils;
import com.procureflow.security.UserPrincipal;
import com.procureflow.supplier.QuotationDtos.ComparisonDto;
import com.procureflow.supplier.QuotationDtos.ComparisonRow;
import com.procureflow.supplier.QuotationDtos.LineDto;
import com.procureflow.supplier.QuotationDtos.LineInput;
import com.procureflow.supplier.QuotationDtos.QuotationDto;
import com.procureflow.supplier.QuotationDtos.QuotationInput;
import com.procureflow.supplier.QuotationDtos.QuotationSummary;
import com.procureflow.supplier.QuotationDtos.QuotationUpdate;
import com.procureflow.supplier.SupplierDtos.SupplierDto;
import com.procureflow.supplier.SupplierDtos.SupplierRef;
import com.procureflow.user.User;
import com.procureflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuotationService {

    private final QuotationRepository quotations;
    private final SupplierRepository suppliers;
    private final PurchaseRequestRepository requests;
    private final UserRepository users;
    private final AuditService audit;

    public QuotationService(QuotationRepository quotations, SupplierRepository suppliers,
                            PurchaseRequestRepository requests, UserRepository users, AuditService audit) {
        this.quotations = quotations;
        this.suppliers = suppliers;
        this.requests = requests;
        this.users = users;
        this.audit = audit;
    }

    // ---- commands --------------------------------------------------------------------------------------------

    @Transactional
    public QuotationDto create(QuotationInput in) {
        UserPrincipal p = SecurityUtils.current();
        // The request row is locked so a purchase order cannot be created for it while a quotation is recorded.
        PurchaseRequest r = requests.findByIdForUpdate(in.requestId())
                .orElseThrow(() -> ApiException.notFound("Request", in.requestId()));
        requireApproved(r);
        Supplier s = suppliers.findById(in.supplierId())
                .orElseThrow(() -> ApiException.notFound("Supplier", in.supplierId()));
        if (!s.isActive()) {
            throw ApiException.badRequest("Supplier '" + s.getName() + "' is inactive");
        }
        if (!s.supplies(r.getCategory())) {
            throw ApiException.badRequest("Supplier '" + s.getName() + "' is not registered for the category '"
                    + r.getCategory().getName() + "'");
        }
        if (quotations.existsByRequestIdAndSupplierId(r.getId(), s.getId())) {
            throw ApiException.conflict(
                    "This supplier already has a quotation for the request. Edit the existing quotation instead.");
        }
        User creator = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User", p.id()));

        Quotation q = new Quotation();
        q.setRequest(r);
        q.setSupplier(s);
        q.setCreatedBy(creator);
        q.setStatus(QuotationStatus.SUBMITTED);
        fill(q, r, in.taxPercent(), in.deliveryDays(), in.paymentTerms(), in.validUntil(), in.notes(), in.lines());
        q = quotations.save(q);
        audit.log("CREATE", "QUOTATION", q.getId(), "Recorded quotation from '" + s.getName() + "' for "
                + r.getRequestNumber() + " (total " + q.getTotalAmount().toPlainString() + ")");
        return toDto(q);
    }

    @Transactional
    public QuotationDto update(Long id, QuotationUpdate in) {
        Quotation q = quotations.findById(id).orElseThrow(() -> ApiException.notFound("Quotation", id));
        PurchaseRequest r = requests.findByIdForUpdate(q.getRequest().getId())
                .orElseThrow(() -> ApiException.notFound("Request", q.getRequest().getId()));
        requireApproved(r);
        if (q.getStatus() != QuotationStatus.SUBMITTED) {
            throw ApiException.conflict("Only open quotations can be edited (current status: " + q.getStatus() + ")");
        }
        fill(q, r, in.taxPercent(), in.deliveryDays(), in.paymentTerms(), in.validUntil(), in.notes(), in.lines());
        audit.log("UPDATE", "QUOTATION", q.getId(), "Updated quotation from '" + q.getSupplier().getName()
                + "' for " + r.getRequestNumber() + " (total " + q.getTotalAmount().toPlainString() + ")");
        return toDto(q);
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public QuotationDto get(Long id) {
        return toDto(quotations.findById(id).orElseThrow(() -> ApiException.notFound("Quotation", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuotationSummary> list(Long requestId, Long supplierId, QuotationStatus status, int page,
                                               int size) {
        Specification<Quotation> spec = (root, query, cb) -> cb.conjunction();
        if (requestId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("request").get("id"), requestId));
        }
        if (supplierId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Quotation> result = quotations.findAll(spec,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result, q -> new QuotationSummary(q.getId(), q.getRequest().getId(),
                q.getRequest().getRequestNumber(), q.getRequest().getTitle(), SupplierRef.from(q.getSupplier()),
                q.getTotalAmount(), q.getDeliveryDays(), q.getStatus(), q.getValidUntil(), q.isExpired(),
                q.getCreatedAt()));
    }

    /** Active suppliers registered for the request's category: the only ones allowed to quote. */
    @Transactional(readOnly = true)
    public List<SupplierDto> eligibleSuppliers(Long requestId) {
        PurchaseRequest r = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request", requestId));
        return suppliers.findEligibleForCategory(r.getCategory().getId()).stream().map(SupplierDto::from).toList();
    }

    /**
     * Side-by-side comparison. Open, unexpired offers from active suppliers are scored on price (60%) and
     * delivery time (40%); the best score is flagged as recommended. The officer still makes the final choice.
     */
    @Transactional(readOnly = true)
    public ComparisonDto compare(Long requestId) {
        PurchaseRequest r = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request", requestId));
        if (r.getStatus() == RequestStatus.DRAFT || r.getStatus() == RequestStatus.SUBMITTED
                || r.getStatus() == RequestStatus.REJECTED || r.getStatus() == RequestStatus.CANCELLED) {
            throw ApiException.badRequest("Quotations can only be compared for approved requests");
        }
        List<Quotation> all = quotations.findByRequestId(requestId);
        List<Quotation> orderable = all.stream().filter(QuotationService::isOrderable).toList();

        BigDecimal minTotal = orderable.stream().map(Quotation::getTotalAmount).min(Comparator.naturalOrder())
                .orElse(null);
        int minDays = orderable.stream().mapToInt(Quotation::getDeliveryDays).min().orElse(0);

        Map<Long, Integer> scores = new HashMap<>();
        Long bestId = null;
        int bestScore = -1;
        BigDecimal bestTotal = null;
        for (Quotation q : orderable) {
            int score = Pricing.score(minTotal, q.getTotalAmount(), minDays, q.getDeliveryDays());
            scores.put(q.getId(), score);
            boolean better = score > bestScore
                    || (score == bestScore && bestTotal != null && q.getTotalAmount().compareTo(bestTotal) < 0);
            if (better) {
                bestScore = score;
                bestId = q.getId();
                bestTotal = q.getTotalAmount();
            }
        }

        List<ComparisonRow> rows = new ArrayList<>();
        for (Quotation q : all) {
            boolean orderable1 = isOrderable(q);
            boolean lowest = orderable1 && minTotal != null && q.getTotalAmount().compareTo(minTotal) == 0;
            boolean fastest = orderable1 && q.getDeliveryDays() == minDays;
            boolean recommended = orderable1 && q.getId().equals(bestId);
            rows.add(new ComparisonRow(q.getId(), SupplierRef.from(q.getSupplier()), q.getSubtotal(),
                    q.getTaxAmount(), q.getTotalAmount(), q.getTaxPercent(), q.getDeliveryDays(),
                    q.getPaymentTerms(), q.getValidUntil(), q.getStatus(), q.isExpired(), orderable1, lowest,
                    fastest, recommended, scores.getOrDefault(q.getId(), 0),
                    r.getTotalEstimate().subtract(q.getSubtotal())));
        }
        rows.sort(Comparator.comparing(ComparisonRow::orderable).reversed()
                .thenComparing(Comparator.comparingInt(ComparisonRow::score).reversed())
                .thenComparing(ComparisonRow::totalAmount));
        return new ComparisonDto(r.getId(), r.getRequestNumber(), r.getTitle(), r.getStatus().name(),
                r.getTotalEstimate(), rows);
    }

    public static boolean isOrderable(Quotation q) {
        return q.getStatus() == QuotationStatus.SUBMITTED && !q.isExpired() && q.getSupplier().isActive();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private void requireApproved(PurchaseRequest r) {
        if (r.getStatus() != RequestStatus.APPROVED) {
            throw ApiException.conflict("Quotations can only be recorded for approved requests that have no "
                    + "purchase order yet (current status: " + r.getStatus() + ")");
        }
    }

    private void fill(Quotation q, PurchaseRequest r, BigDecimal taxPercent, int deliveryDays, String paymentTerms,
                      LocalDate validUntil, String notes, List<LineInput> lines) {
        if (validUntil != null && validUntil.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("'Valid until' cannot be in the past");
        }
        Map<Long, PurchaseRequestItem> items = new HashMap<>();
        for (PurchaseRequestItem item : r.getItems()) {
            items.put(item.getId(), item);
        }
        Set<Long> seen = new HashSet<>();
        List<QuotationLine> newLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (LineInput input : lines) {
            PurchaseRequestItem item = items.get(input.requestItemId());
            if (item == null) {
                throw ApiException.badRequest("Item " + input.requestItemId() + " does not belong to this request");
            }
            if (!seen.add(input.requestItemId())) {
                throw ApiException.badRequest("Duplicate price for item '" + item.getName() + "'");
            }
            QuotationLine line = new QuotationLine();
            line.setRequestItem(item);
            line.setUnitPrice(Pricing.money(input.unitPrice()));
            newLines.add(line);
            subtotal = subtotal.add(Pricing.lineTotal(item.getQuantity(), line.getUnitPrice()));
        }
        if (seen.size() != items.size()) {
            throw ApiException.badRequest("A unit price is required for every requested item");
        }
        q.getLines().clear();
        for (QuotationLine line : newLines) {
            q.addLine(line);
        }
        Pricing.Totals totals = Pricing.totals(subtotal, taxPercent);
        q.setTaxPercent(taxPercent.setScale(2, RoundingMode.HALF_UP));
        q.setDeliveryDays(deliveryDays);
        q.setPaymentTerms(StringUtils.hasText(paymentTerms) ? paymentTerms.trim() : null);
        q.setValidUntil(validUntil);
        q.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        q.setSubtotal(totals.subtotal());
        q.setTaxAmount(totals.tax());
        q.setTotalAmount(totals.total());
    }

    private QuotationDto toDto(Quotation q) {
        List<LineDto> lines = q.getLines().stream()
                .map(l -> new LineDto(l.getRequestItem().getId(), l.getRequestItem().getName(),
                        l.getRequestItem().getQuantity(), l.getUnitPrice(),
                        Pricing.lineTotal(l.getRequestItem().getQuantity(), l.getUnitPrice())))
                .toList();
        return new QuotationDto(q.getId(), q.getRequest().getId(), q.getRequest().getRequestNumber(),
                q.getRequest().getTitle(), SupplierRef.from(q.getSupplier()), q.getTaxPercent(),
                q.getDeliveryDays(), q.getPaymentTerms(), q.getValidUntil(), q.getNotes(), q.getStatus(),
                q.getSubtotal(), q.getTaxAmount(), q.getTotalAmount(), q.isExpired(), lines,
                q.getCreatedBy().getFullName(), q.getCreatedAt());
    }
}
