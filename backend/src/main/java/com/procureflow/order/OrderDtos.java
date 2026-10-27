package com.procureflow.order;

import com.procureflow.supplier.SupplierDtos.SupplierDto;
import com.procureflow.supplier.SupplierDtos.SupplierRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ---- input -----------------------------------------------------------------------------------------------

    public record CreateOrderRequest(@NotNull Long requestId, @NotNull Long quotationId,
                                     @Size(max = 1000) String notes) {
    }

    public record CancelOrderRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record DeliveryLineInput(@NotNull Long poItemId, @Min(1) int quantity) {
    }

    public record DeliveryInput(@NotNull @PastOrPresent LocalDate receivedDate, @Size(max = 500) String notes,
                                @NotEmpty @Valid List<DeliveryLineInput> lines) {
    }

    // ---- output ----------------------------------------------------------------------------------------------

    public record OrderItemDto(Long id, String name, String description, int quantityOrdered, int quantityReceived,
                               int outstanding, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record DeliveryLineDto(Long poItemId, String itemName, int quantity) {
    }

    public record DeliveryDto(Long id, LocalDate receivedDate, String notes, String receivedByName,
                              Instant createdAt, List<DeliveryLineDto> lines) {
    }

    public record RequestRef(Long id, String requestNumber, String title, Long requesterId, String requesterName) {
    }

    public record OrderSummary(Long id, String poNumber, PurchaseOrderStatus status, Long requestId,
                               String requestNumber, String requestTitle, String requesterName,
                               SupplierRef supplier, BigDecimal totalAmount, Instant issuedAt,
                               LocalDate expectedDeliveryDate, Instant createdAt, int progressPercent) {
    }

    public record OrderDetail(Long id, String poNumber, PurchaseOrderStatus status, RequestRef request,
                              SupplierDto supplier, BigDecimal subtotal, BigDecimal taxPercent,
                              BigDecimal taxAmount, BigDecimal totalAmount, int deliveryDays, String paymentTerms,
                              Instant createdAt, Instant issuedAt, LocalDate expectedDeliveryDate, String notes,
                              String createdByName, List<OrderItemDto> items, List<DeliveryDto> deliveries,
                              int progressPercent, Set<String> allowedActions) {
    }
}
