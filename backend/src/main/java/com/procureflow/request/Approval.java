package com.procureflow.request;

import com.procureflow.common.BaseEntity;
import com.procureflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "approvals", indexes = {
        @Index(name = "idx_approval_request", columnList = "request_id"),
        @Index(name = "idx_approval_approver", columnList = "approver_id")
})
@Getter
@Setter
public class Approval extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
