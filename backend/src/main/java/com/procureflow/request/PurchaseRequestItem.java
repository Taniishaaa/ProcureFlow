package com.procureflow.request;

import com.procureflow.common.BaseEntity;
import com.procureflow.common.Pricing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_request_items")
@Getter
@Setter
public class PurchaseRequestItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "estimated_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedUnitPrice;

    public BigDecimal lineTotal() {
        return Pricing.lineTotal(quantity, estimatedUnitPrice);
    }
}
