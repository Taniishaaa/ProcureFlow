package com.procureflow.supplier;

import com.procureflow.supplier.SupplierDtos.SupplierRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuotationDtos {

    private QuotationDtos() {
    }

    public record LineInput(
            @NotNull Long requestItemId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {
    }

    public record QuotationInput(
            @NotNull Long requestId,
            @NotNull Long supplierId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxPercent,
            @Min(1) @Max(365) int deliveryDays,
            @Size(max = 200) String paymentTerms,
            LocalDate validUntil,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<LineInput> lines) {
    }

    public record QuotationUpdate(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxPercent,
            @Min(1) @Max(365) int deliveryDays,
            @Size(max = 200) String paymentTerms,
            LocalDate validUntil,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<LineInput> lines) {
    }

    public record LineDto(Long requestItemId, String itemName, int quantity, BigDecimal unitPrice,
                          BigDecimal lineTotal) {
    }

    public record QuotationDto(Long id, Long requestId, String requestNumber, String requestTitle,
                               SupplierRef supplier, BigDecimal taxPercent, int deliveryDays, String paymentTerms,
                               LocalDate validUntil, String notes, QuotationStatus status, BigDecimal subtotal,
                               BigDecimal taxAmount, BigDecimal totalAmount, boolean expired, List<LineDto> lines,
                               String createdByName, Instant createdAt) {
    }

    public record QuotationSummary(Long id, Long requestId, String requestNumber, String requestTitle,
                                   SupplierRef supplier, BigDecimal totalAmount, int deliveryDays,
                                   QuotationStatus status, LocalDate validUntil, boolean expired,
                                   Instant createdAt) {
    }

    public record ComparisonRow(Long quotationId, SupplierRef supplier, BigDecimal subtotal, BigDecimal taxAmount,
                                BigDecimal totalAmount, BigDecimal taxPercent, int deliveryDays,
                                String paymentTerms, LocalDate validUntil, QuotationStatus status,
                                boolean expired, boolean orderable, boolean lowestPrice, boolean fastestDelivery,
                                boolean recommended, int score, BigDecimal savingsVsEstimate) {
    }

    public record ComparisonDto(Long requestId, String requestNumber, String requestTitle, String requestStatus,
                                BigDecimal totalEstimate, List<ComparisonRow> rows) {
    }
}
