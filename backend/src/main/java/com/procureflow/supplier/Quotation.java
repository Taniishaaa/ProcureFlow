package com.procureflow.supplier;

import com.procureflow.common.BaseEntity;
import com.procureflow.request.PurchaseRequest;
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
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A supplier's offer for one approved purchase request, priced per requested item. */
@Entity
@Table(name = "quotations",
        uniqueConstraints = @UniqueConstraint(name = "uk_quotation_request_supplier",
                columnNames = {"request_id", "supplier_id"}),
        indexes = {
                @Index(name = "idx_quotation_request", columnList = "request_id"),
                @Index(name = "idx_quotation_supplier", columnList = "supplier_id")
        })
@Getter
@Setter
public class Quotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent = BigDecimal.ZERO;

    @Column(name = "delivery_days", nullable = false)
    private int deliveryDays;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.SUBMITTED;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuotationLine> lines = new ArrayList<>();

    public void addLine(QuotationLine line) {
        line.setQuotation(this);
        lines.add(line);
    }

    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDate.now());
    }
}
