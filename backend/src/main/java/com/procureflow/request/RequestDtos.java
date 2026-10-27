package com.procureflow.request;

import com.procureflow.common.Refs.CategoryRef;
import com.procureflow.common.Refs.UserRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class RequestDtos {

    private RequestDtos() {
    }

    // ---- input -----------------------------------------------------------------------------------------------

    public record ItemInput(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description,
            @Min(1) @Max(100000) int quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal estimatedUnitPrice) {
    }

    /** Used for both creating and editing a request. When {@code submit} is true on create, it is submitted immediately. */
    public record SaveRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 2000) String justification,
            @NotEmpty @Size(max = 50) @Valid List<ItemInput> items,
            Boolean submit) {
    }

    public record DecisionInput(@Size(max = 1000) String comments) {
    }

    // ---- output ----------------------------------------------------------------------------------------------

    public record ItemDto(Long id, String name, String description, int quantity, BigDecimal estimatedUnitPrice,
                          BigDecimal lineTotal) {
    }

    public record ApprovalDto(Long id, ApprovalDecision decision, String comments, Instant decidedAt,
                              UserRef approver) {
    }

    public record RequestSummary(Long id, String requestNumber, String title, RequestStatus status,
                                 CategoryRef category, UserRef requester, BigDecimal totalEstimate, int itemCount,
                                 Instant submittedAt, Instant createdAt, Long quotationCount) {
    }

    public record RequestDetail(Long id, String requestNumber, String title, RequestStatus status,
                                CategoryRef category, UserRef requester, String justification,
                                BigDecimal totalEstimate, Instant submittedAt, Instant createdAt, Instant updatedAt,
                                List<ItemDto> items, List<ApprovalDto> approvals, Long purchaseOrderId,
                                String purchaseOrderNumber, String purchaseOrderStatus, Set<String> allowedActions) {
    }

    public record ApprovalRecord(Long id, Long requestId, String requestNumber, String requestTitle,
                                 String requesterName, BigDecimal totalEstimate, ApprovalDecision decision,
                                 String comments, Instant decidedAt, String approverName) {
    }
}
