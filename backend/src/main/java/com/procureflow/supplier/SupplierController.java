package com.procureflow.supplier;

import com.procureflow.common.PageResponse;
import com.procureflow.supplier.SupplierDtos.ActiveInput;
import com.procureflow.supplier.SupplierDtos.SupplierDetail;
import com.procureflow.supplier.SupplierDtos.SupplierDto;
import com.procureflow.supplier.SupplierDtos.SupplierInput;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
@Tag(name = "Suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public PageResponse<SupplierDto> list(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return service.list(search, active, categoryId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public SupplierDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public SupplierDto create(@Valid @RequestBody SupplierInput body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public SupplierDto update(@PathVariable Long id, @Valid @RequestBody SupplierInput body) {
        return service.update(id, body);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public SupplierDto setActive(@PathVariable Long id, @RequestBody ActiveInput body) {
        return service.setActive(id, body.active());
    }
}
