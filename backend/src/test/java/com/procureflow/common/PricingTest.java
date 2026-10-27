package com.procureflow.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingTest {

    @Test
    void lineTotalMultipliesAndRoundsToTwoDecimals() {
        assertThat(Pricing.lineTotal(3, new BigDecimal("19.99"))).isEqualByComparingTo("59.97");
        assertThat(Pricing.lineTotal(3, new BigDecimal("19.99")).scale()).isEqualTo(2);
    }

    @Test
    void totalsAddTaxToSubtotal() {
        Pricing.Totals totals = Pricing.totals(new BigDecimal("1000"), new BigDecimal("18"));
        assertThat(totals.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(totals.tax()).isEqualByComparingTo("180.00");
        assertThat(totals.total()).isEqualByComparingTo("1180.00");
    }

    @Test
    void taxUsesHalfUpRounding() {
        // 333.33 * 18% = 59.9994 -> 60.00
        assertThat(Pricing.tax(new BigDecimal("333.33"), new BigDecimal("18"))).isEqualByComparingTo("60.00");
        // 0.05 * 10% = 0.005 -> 0.01
        assertThat(Pricing.tax(new BigDecimal("0.05"), new BigDecimal("10"))).isEqualByComparingTo("0.01");
    }

    @Test
    void zeroTaxLeavesTotalUnchanged() {
        Pricing.Totals totals = Pricing.totals(new BigDecimal("250.50"), BigDecimal.ZERO);
        assertThat(totals.tax()).isEqualByComparingTo("0.00");
        assertThat(totals.total()).isEqualByComparingTo("250.50");
    }

    @Test
    void cheapestAndFastestOfferScoresFullMarks() {
        assertThat(Pricing.score(new BigDecimal("1000"), new BigDecimal("1000"), 5, 5)).isEqualTo(100);
    }

    @Test
    void scoreWeighsPriceSixtyAndDeliveryForty() {
        // twice the price and twice the delivery time -> half marks on both criteria
        assertThat(Pricing.score(new BigDecimal("1000"), new BigDecimal("2000"), 5, 10)).isEqualTo(50);
        // cheapest but twice as slow: 0.6 * 1.0 + 0.4 * 0.5 = 0.8
        assertThat(Pricing.score(new BigDecimal("1000"), new BigDecimal("1000"), 5, 10)).isEqualTo(80);
        // fastest but twice the price: 0.6 * 0.5 + 0.4 * 1.0 = 0.7
        assertThat(Pricing.score(new BigDecimal("1000"), new BigDecimal("2000"), 5, 5)).isEqualTo(70);
    }

    @Test
    void invalidInputsScoreZero() {
        assertThat(Pricing.score(new BigDecimal("1000"), BigDecimal.ZERO, 5, 5)).isZero();
        assertThat(Pricing.score(new BigDecimal("1000"), new BigDecimal("1000"), 5, 0)).isZero();
    }

    @Test
    void toBigDecimalHandlesNullAndNumbers() {
        assertThat(Pricing.toBigDecimal(null)).isEqualByComparingTo("0");
        assertThat(Pricing.toBigDecimal(12L)).isEqualByComparingTo("12");
        assertThat(Pricing.toBigDecimal(new BigDecimal("1.50"))).isEqualByComparingTo("1.5");
    }
}
