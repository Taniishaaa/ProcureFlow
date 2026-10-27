package com.procureflow.supplier;

import com.procureflow.common.PageResponse;
import com.procureflow.supplier.QuotationDtos.ComparisonDto;
import com.procureflow.supplier.QuotationDtos.QuotationDto;
import com.procureflow.supplier.QuotationDtos.QuotationInput;
import com.procureflow.supplier.QuotationDtos.QuotationSummary;
import com.procureflow.supplier.QuotationDtos.QuotationUpdate;
import com.procureflow.supplier.SupplierDtos.SupplierDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @GetMapping("/quotations")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public PageResponse<QuotationSummary> list(@RequestParam(required = false) Long requestId,
                                               @RequestParam(required = false) Long supplierId,
                                               @RequestParam(required = false) QuotationStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return service.list(requestId, supplierId, status, page, size);
    }

    @GetMapping("/quotations/{id}")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public QuotationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/quotations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public QuotationDto create(@Valid @RequestBody QuotationInput body) {
        return service.create(body);
    }

    @PutMapping("/quotations/{id}")
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public QuotationDto update(@PathVariable Long id, @Valid @RequestBody QuotationUpdate body) {
        return service.update(id, body);
    }

    @GetMapping("/requests/{requestId}/comparison")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public ComparisonDto compare(@PathVariable Long requestId) {
        return service.compare(requestId);
    }

    @GetMapping("/requests/{requestId}/eligible-suppliers")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public List<SupplierDto> eligibleSuppliers(@PathVariable Long requestId) {
        return service.eligibleSuppliers(requestId);
    }
}
