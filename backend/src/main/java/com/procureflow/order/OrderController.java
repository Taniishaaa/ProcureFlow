package com.procureflow.order;

import com.procureflow.audit.AuditLogDto;
import com.procureflow.common.PageResponse;
import com.procureflow.order.OrderDtos.CancelOrderRequest;
import com.procureflow.order.OrderDtos.CreateOrderRequest;
import com.procureflow.order.OrderDtos.DeliveryInput;
import com.procureflow.order.OrderDtos.OrderDetail;
import com.procureflow.order.OrderDtos.OrderSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@Tag(name = "Purchase Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public OrderDetail create(@Valid @RequestBody CreateOrderRequest body) {
        return service.create(body);
    }

    /** Employees see only the issued orders of their own requests; procurement and admin see all. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','PROCUREMENT_OFFICER','ADMIN')")
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(status, supplierId, search, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','PROCUREMENT_OFFICER','ADMIN')")
    public OrderDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('EMPLOYEE','PROCUREMENT_OFFICER','ADMIN')")
    public List<AuditLogDto> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public OrderDetail issue(@PathVariable Long id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public OrderDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequest body) {
        return service.cancel(id, body.reason());
    }

    @PostMapping("/{id}/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROCUREMENT_OFFICER')")
    public OrderDetail recordDelivery(@PathVariable Long id, @Valid @RequestBody DeliveryInput body) {
        return service.recordDelivery(id, body);
    }
}
