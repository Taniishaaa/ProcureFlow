package com.procureflow.request;

import com.procureflow.category.Category;
import com.procureflow.common.BaseEntity;
import com.procureflow.common.Pricing;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_pr_number", columnNames = "request_number"),
        indexes = {
                @Index(name = "idx_pr_status", columnList = "status"),
                @Index(name = "idx_pr_requester", columnList = "requester_id"),
                @Index(name = "idx_pr_category", columnList = "category_id")
        })
@Getter
@Setter
public class PurchaseRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 40)
    private String requestNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "total_estimate", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEstimate = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Optimistic lock: a concurrent modification fails with a 409 instead of silently overwriting. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseRequestItem> items = new ArrayList<>();

    public void addItem(PurchaseRequestItem item) {
        item.setRequest(this);
        items.add(item);
    }

    public void clearItems() {
        items.clear();
    }

    public void recalcTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequestItem item : items) {
            total = total.add(item.lineTotal());
        }
        this.totalEstimate = Pricing.money(total);
    }
}
