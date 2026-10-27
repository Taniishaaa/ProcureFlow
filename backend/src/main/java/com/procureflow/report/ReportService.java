package com.procureflow.report;

import com.procureflow.common.Paging;
import com.procureflow.common.Pricing;
import com.procureflow.order.PurchaseOrderRepository;
import com.procureflow.order.PurchaseOrderStatus;
import com.procureflow.report.ReportDtos.Bucket;
import com.procureflow.report.ReportDtos.ReportSummary;
import com.procureflow.request.PurchaseRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Spend and workflow summaries. "Spend" counts purchase orders that were issued and not cancelled. */
@Service
public class ReportService {

    private static final Set<PurchaseOrderStatus> SPEND_STATUSES = EnumSet.of(
            PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.COMPLETED);
    private static final Instant FAR_FUTURE = Instant.parse("2999-12-31T00:00:00Z");

    private final PurchaseOrderRepository orders;
    private final PurchaseRequestRepository requests;

    public ReportService(PurchaseOrderRepository orders, PurchaseRequestRepository requests) {
        this.orders = orders;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public ReportSummary summary(LocalDate from, LocalDate to) {
        Instant start = from == null ? Instant.EPOCH : Paging.startOf(from);
        Instant end = to == null ? FAR_FUTURE : Paging.endExclusive(to);

        List<Bucket> byCategory = toBuckets(orders.spendByCategory(SPEND_STATUSES, start, end));
        List<Bucket> bySupplier = toBuckets(orders.spendBySupplier(SPEND_STATUSES, start, end));
        List<Bucket> ordersByStatus = toBuckets(orders.statusSummary(start, end));
        List<Bucket> requestsByStatus = toBuckets(requests.statusSummary(start, end));

        BigDecimal totalSpend = byCategory.stream().map(Bucket::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long orderCount = byCategory.stream().mapToLong(Bucket::count).sum();
        BigDecimal average = orderCount == 0 ? BigDecimal.ZERO
                : totalSpend.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        return new ReportSummary(Pricing.money(totalSpend), orderCount, average, byCategory, bySupplier,
                monthly(start, end), ordersByStatus, requestsByStatus);
    }

    /** Rows are (label, amount, count) or (label, count, amount) depending on the query; both shapes are handled. */
    private List<Bucket> toBuckets(List<Object[]> rows) {
        List<Bucket> buckets = new ArrayList<>();
        for (Object[] row : rows) {
            String label = String.valueOf(row[0]);
            BigDecimal amount;
            long count;
            if (row[1] instanceof Long || row[1] instanceof Integer) {
                count = ((Number) row[1]).longValue();
                amount = Pricing.money(Pricing.toBigDecimal(row[2]));
            } else {
                amount = Pricing.money(Pricing.toBigDecimal(row[1]));
                count = ((Number) row[2]).longValue();
            }
            buckets.add(new Bucket(label, amount, count));
        }
        return buckets;
    }

    private List<Bucket> monthly(Instant start, Instant end) {
        Map<String, BigDecimal> amounts = new TreeMap<>();
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : orders.issuedAmounts(SPEND_STATUSES, start, end)) {
            Instant issued = (Instant) row[0];
            String month = YearMonth.from(issued.atZone(ZoneOffset.UTC)).toString();
            amounts.merge(month, Pricing.toBigDecimal(row[1]), BigDecimal::add);
            counts.merge(month, 1L, Long::sum);
        }
        List<Bucket> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : amounts.entrySet()) {
            result.add(new Bucket(e.getKey(), Pricing.money(e.getValue()), counts.get(e.getKey())));
        }
        return result;
    }
}
