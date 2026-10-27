package com.procureflow.request;

import com.procureflow.common.PageResponse;
import com.procureflow.request.RequestDtos.DecisionInput;
import com.procureflow.request.RequestDtos.RequestDetail;
import com.procureflow.request.RequestDtos.RequestSummary;
import com.procureflow.request.RequestDtos.SaveRequest;
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

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Purchase Requests")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    // ---- employee --------------------------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public RequestDetail create(@Valid @RequestBody SaveRequest body) {
        return service.create(body);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public PageResponse<RequestSummary> mine(@RequestParam(required = false) RequestStatus status,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return service.listMine(status, search, page, size);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public RequestDetail update(@PathVariable Long id, @Valid @RequestBody SaveRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public RequestDetail submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public RequestDetail cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    // ---- manager ---------------------------------------------------------------------------------------------

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageResponse<RequestSummary> pendingApproval(@RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return service.listPendingApproval(search, page, size);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public RequestDetail approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionInput body) {
        return service.approve(id, body == null ? null : body.comments());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public RequestDetail reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionInput body) {
        return service.reject(id, body == null ? null : body.comments());
    }

    // ---- procurement -----------------------------------------------------------------------------------------

    @GetMapping("/approved")
    @PreAuthorize("hasAnyRole('PROCUREMENT_OFFICER','ADMIN')")
    public PageResponse<RequestSummary> approved(@RequestParam(required = false) String search,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return service.listApproved(search, categoryId, page, size);
    }

    // ---- administrator ---------------------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<RequestSummary> all(@RequestParam(required = false) RequestStatus status,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return service.listAll(status, search, categoryId, page, size);
    }

    // ---- shared ----------------------------------------------------------------------------------------------

    /** Access is checked per record in the service (owner, manager, procurement or admin). */
    @GetMapping("/{id}")
    public RequestDetail get(@PathVariable Long id) {
        return service.get(id);
    }
}
