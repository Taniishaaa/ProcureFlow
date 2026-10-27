package com.procureflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money arithmetic and quotation scoring, kept free of framework code so it is easy to unit test. */
public final class Pricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Pricing() {
    }

    public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(int quantity, BigDecimal unitPrice) {
        return money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal tax(BigDecimal subtotal, BigDecimal taxPercent) {
        return money(subtotal.multiply(taxPercent).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public static Totals totals(BigDecimal subtotal, BigDecimal taxPercent) {
        BigDecimal sub = money(subtotal);
        BigDecimal tax = tax(sub, taxPercent);
        return new Totals(sub, tax, money(sub.add(tax)));
    }

    /**
     * Weighted score from 0 to 100: 60% price, 40% delivery time. The cheapest and the fastest offers each
     * earn full marks for their criterion.
     */
    public static int score(BigDecimal minTotal, BigDecimal total, int minDays, int days) {
        if (total == null || total.signum() <= 0 || days <= 0) {
            return 0;
        }
        double price = minTotal.divide(total, 6, RoundingMode.HALF_UP).doubleValue();
        double delivery = (double) minDays / (double) days;
        return (int) Math.round((0.6 * price + 0.4 * delivery) * 100.0);
    }

    /** Converts aggregate query results (BigDecimal, Long, Double, ...) to BigDecimal. */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
