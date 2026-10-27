package com.procureflow.report;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record Bucket(String label, BigDecimal amount, long count) {
    }

    public record ReportSummary(BigDecimal totalSpend, long orderCount, BigDecimal averageOrderValue,
                                List<Bucket> spendByCategory, List<Bucket> spendBySupplier,
                                List<Bucket> spendByMonth, List<Bucket> ordersByStatus,
                                List<Bucket> requestsByStatus) {
    }
}
