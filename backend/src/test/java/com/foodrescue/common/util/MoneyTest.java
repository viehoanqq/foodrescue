package com.foodrescue.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void commissionMatchesSampleData() {
        // Khớp dữ liệu mẫu trong foodrescue_schema.sql
        assertThat(Money.commission(vnd(87000), new BigDecimal("15.00"))).isEqualByComparingTo("13050");
        assertThat(Money.storeEarning(vnd(87000), new BigDecimal("15.00"))).isEqualByComparingTo("73950");
        assertThat(Money.commission(vnd(22000), new BigDecimal("15.00"))).isEqualByComparingTo("3300");
    }

    @Test
    void commissionRoundsHalfUpLikeMySql() {
        // 33.333 x 15% = 4.999,95 -> 5.000 (MySQL ROUND cũng ra 5000)
        assertThat(Money.commission(vnd(33333), new BigDecimal("15.00"))).isEqualByComparingTo("5000");
        // 10.001 x 12,5% = 1.250,125 -> 1.250
        assertThat(Money.commission(vnd(10001), new BigDecimal("12.50"))).isEqualByComparingTo("1250");
    }

    @Test
    void rescuePriceRule() {
        assertThat(Money.isValidRescuePrice(vnd(100000), vnd(60000))).isTrue();
        assertThat(Money.isValidRescuePrice(vnd(100000), vnd(60001))).isFalse();
        assertThat(Money.isValidRescuePrice(vnd(100000), vnd(0))).isFalse();
    }

    @Test
    void discountPercent() {
        assertThat(Money.discountPercent(vnd(80000), vnd(30000))).isEqualTo(63);
        assertThat(Money.discountPercent(vnd(120000), vnd(45000))).isEqualTo(63);
    }
}
