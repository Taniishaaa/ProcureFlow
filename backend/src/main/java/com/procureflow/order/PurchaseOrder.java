package com.procureflow.order;

import com.procureflow.common.BaseEntity;
import com.procureflow.request.PurchaseRequest;
import com.procureflow.supplier.Quotation;
import com.procureflow.supplier.Supplier;
import com.procureflow.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_po_number", columnNames = "po_number"),
        indexes = {
                @Index(name = "idx_po_status", columnList = "status"),
                @Index(name = "idx_po_supplier", columnList = "supplier_id"),
                @Index(name = "idx_po_request", columnList = "request_id")
        })
@Getter
@Setter
public class PurchaseOrder extends BaseEntity {

    @Column(name = "po_number", nullable = false, length = 40)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "delivery_days", nullable = false)
    private int deliveryDays;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    public void addItem(PurchaseOrderItem item) {
        item.setPurchaseOrder(this);
        items.add(item);
    }

    public int totalOrdered() {
        return items.stream().mapToInt(PurchaseOrderItem::getQuantityOrdered).sum();
    }

    public int totalReceived() {
        return items.stream().mapToInt(PurchaseOrderItem::getQuantityReceived).sum();
    }

    public int progressPercent() {
        int ordered = totalOrdered();
        return ordered == 0 ? 0 : (int) Math.round(100.0 * totalReceived() / ordered);
    }
}
