package com.procureflow.supplier;

public enum QuotationStatus {
    /** Recorded and available for selection. */
    SUBMITTED,
    /** Chosen for a purchase order. */
    SELECTED,
    /** Another offer was chosen. */
    REJECTED
}
