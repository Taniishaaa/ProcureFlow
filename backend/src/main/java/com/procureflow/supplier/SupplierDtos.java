package com.procureflow.supplier;

import com.procureflow.common.Refs.CategoryRef;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierInput(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 150) String contactName,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 40) String phone,
            @Size(max = 500) String address,
            @Size(max = 50) String taxId,
            @NotEmpty Set<Long> categoryIds) {
    }

    public record ActiveInput(boolean active) {
    }

    public record SupplierDto(Long id, String name, String contactName, String email, String phone, String address,
                              String taxId, boolean active, List<CategoryRef> categories, Instant createdAt) {
        public static SupplierDto from(Supplier s) {
            List<CategoryRef> cats = s.getCategories().stream()
                    .map(CategoryRef::from)
                    .sorted(Comparator.comparing(CategoryRef::name))
                    .toList();
            return new SupplierDto(s.getId(), s.getName(), s.getContactName(), s.getEmail(), s.getPhone(),
                    s.getAddress(), s.getTaxId(), s.isActive(), cats, s.getCreatedAt());
        }
    }

    public record SupplierRef(Long id, String name, String email) {
        public static SupplierRef from(Supplier s) {
            return new SupplierRef(s.getId(), s.getName(), s.getEmail());
        }
    }

    public record QuotationBrief(Long id, Long requestId, String requestNumber, BigDecimal totalAmount,
                                 int deliveryDays, QuotationStatus status, Instant createdAt) {
    }

    public record OrderBrief(Long id, String poNumber, String status, BigDecimal totalAmount, Instant issuedAt,
                             Instant createdAt) {
    }

    public record SupplierDetail(SupplierDto supplier, List<QuotationBrief> quotations, List<OrderBrief> orders) {
    }
}
