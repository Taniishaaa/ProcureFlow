package com.procureflow.order;

public enum PurchaseOrderStatus {
    /** Generated from a quotation but not yet sent to the supplier. */
    DRAFT,
    /** Issued to the supplier; awaiting delivery. */
    ISSUED,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED
}
