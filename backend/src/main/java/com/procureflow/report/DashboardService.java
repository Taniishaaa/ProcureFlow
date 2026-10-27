package com.procureflow.report;

import com.procureflow.audit.AuditService;
import com.procureflow.order.OrderService;
import com.procureflow.order.PurchaseOrderRepository;
import com.procureflow.order.PurchaseOrderStatus;
import com.procureflow.common.Pricing;
import com.procureflow.request.ApprovalDecision;
import com.procureflow.request.ApprovalRepository;
import com.procureflow.request.PurchaseRequestRepository;
import com.procureflow.request.RequestService;
import com.procureflow.request.RequestStatus;
import com.procureflow.security.SecurityUtils;
import com.procureflow.security.UserPrincipal;
import com.procureflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the role-specific dashboard payload. Heavy lifting is done by counting queries, not by loading rows. */
@Service
public class DashboardService {

    private static final List<PurchaseOrderStatus> AWAITING_DELIVERY =
            List.of(PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
    private static final List<PurchaseOrderStatus> ACTIVE_ORDERS =
            List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final PurchaseRequestRepository requests;
    private final ApprovalRepository approvals;
    private final PurchaseOrderRepository orders;
    private final UserRepository users;
    private final RequestService requestService;
    private final OrderService orderService;
    private final AuditService audit;

    public DashboardService(PurchaseRequestRepository requests, ApprovalRepository approvals,
                            PurchaseOrderRepository orders, UserRepository users, RequestService requestService,
                            OrderService orderService, AuditService audit) {
        this.requests = requests;
        this.approvals = approvals;
        this.orders = orders;
        this.users = users;
        this.requestService = requestService;
        this.orderService = orderService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        UserPrincipal p = SecurityUtils.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", p.role());
        switch (p.role()) {
            case EMPLOYEE -> employee(p, out);
            case MANAGER -> manager(p, out);
            case PROCUREMENT_OFFICER -> procurement(out);
            case ADMIN -> admin(out);
        }
        return out;
    }

    private void employee(UserPrincipal p, Map<String, Object> out) {
        Map<String, Long> byStatus = countMap(RequestStatus.class, requests.statusCountsForRequester(p.id()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        out.put("totalRequests", total);
        out.put("requestsByStatus", byStatus);
        out.put("pendingApprovals", byStatus.get(RequestStatus.SUBMITTED.name()));
        out.put("activeOrders", orders.countByRequestRequesterIdAndStatusIn(p.id(), AWAITING_DELIVERY));
        out.put("recentRequests", requestService.listMine(null, null, 0, 5).content());
        out.put("recentOrders", orderService.list(null, null, null, null, null, 0, 5).content());
    }

    private void manager(UserPrincipal p, Map<String, Object> out) {
        out.put("pendingCount", requests.countByStatus(RequestStatus.SUBMITTED));
        out.put("pendingValue", Pricing.toBigDecimal(requests.sumTotalByStatus(RequestStatus.SUBMITTED)));
        out.put("approvedByMe", approvals.countByApproverIdAndDecision(p.id(), ApprovalDecision.APPROVED));
        out.put("rejectedByMe", approvals.countByApproverIdAndDecision(p.id(), ApprovalDecision.REJECTED));
        out.put("pendingRequests", requestService.listPendingApproval(null, 0, 5).content());
        out.put("recentDecisions", requestService.listDecisions(null, null, 0, 5).content());
    }

    private void procurement(Map<String, Object> out) {
        out.put("awaitingProcurement", requests.countByStatus(RequestStatus.APPROVED));
        out.put("activeOrders", orders.countByStatusIn(ACTIVE_ORDERS));
        out.put("pendingDeliveries", orders.countByStatusIn(AWAITING_DELIVERY));
        out.put("openOrderValue", Pricing.toBigDecimal(orders.sumTotalByStatusIn(AWAITING_DELIVERY)));
        out.put("approvedRequests", requestService.listApproved(null, null, 0, 5).content());
        out.put("recentOrders", orderService.list(null, null, null, null, null, 0, 5).content());
    }

    private void admin(Map<String, Object> out) {
        out.put("totalUsers", users.count());
        out.put("activeUsers", users.countByActiveTrue());
        out.put("totalRequests", requests.count());
        out.put("totalOrders", orders.count());
        Map<String, Long> pending = new LinkedHashMap<>();
        pending.put("requestsAwaitingApproval", requests.countByStatus(RequestStatus.SUBMITTED));
        pending.put("approvedAwaitingProcurement", requests.countByStatus(RequestStatus.APPROVED));
        pending.put("ordersAwaitingDelivery", orders.countByStatusIn(AWAITING_DELIVERY));
        out.put("pendingActions", pending);
        out.put("requestsByStatus", countMap(RequestStatus.class, requests.statusCounts()));
        out.put("ordersByStatus", countMap(PurchaseOrderStatus.class, orders.statusCounts()));
        out.put("recentActivity", audit.recent(8));
    }

    private static <E extends Enum<E>> Map<String, Long> countMap(Class<E> type, List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (E constant : type.getEnumConstants()) {
            map.put(constant.name(), 0L);
        }
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }
}
