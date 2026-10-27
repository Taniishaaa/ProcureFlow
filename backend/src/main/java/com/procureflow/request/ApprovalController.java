package com.procureflow.request;

import com.procureflow.common.PageResponse;
import com.procureflow.request.RequestDtos.ApprovalRecord;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals")
public class ApprovalController {

    private final RequestService service;

    public ApprovalController(RequestService service) {
        this.service = service;
    }

    /** Approval history: a manager sees their own decisions, an administrator sees all. */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageResponse<ApprovalRecord> history(@RequestParam(required = false) ApprovalDecision decision,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return service.listDecisions(decision, search, page, size);
    }
}
