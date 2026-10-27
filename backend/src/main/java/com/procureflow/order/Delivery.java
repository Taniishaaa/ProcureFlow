package com.procureflow.order;

import com.procureflow.common.BaseEntity;
import com.procureflow.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One goods-receipt event against a purchase order (a partial or final delivery). */
@Entity
@Table(name = "deliveries", indexes = @Index(name = "idx_delivery_po", columnList = "purchase_order_id"))
@Getter
@Setter
public class Delivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false)
    private User receivedBy;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DeliveryLine> lines = new ArrayList<>();

    public void addLine(DeliveryLine line) {
        line.setDelivery(this);
        lines.add(line);
    }
}
