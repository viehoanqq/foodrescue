package com.foodrescue.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tính tiền dùng chung. Mọi số tiền là VNĐ, số nguyên, kiểu BigDecimal (không dùng double).
 * Công thức phải khớp CHECK chk_order_money trong CSDL:
 * commission = ROUND(total * rate / 100), storeEarning = total - commission.
 */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_RESCUE_RATIO = new BigDecimal("0.6");

    private Money() {
    }

    /** Phí nền tảng, làm tròn đến đồng (HALF_UP, khớp ROUND() của MySQL với số dương). */
    public static BigDecimal commission(BigDecimal totalAmount, BigDecimal commissionRate) {
        return totalAmount.multiply(commissionRate).divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    public static BigDecimal storeEarning(BigDecimal totalAmount, BigDecimal commissionRate) {
        return totalAmount.subtract(commission(totalAmount, commissionRate));
    }

    public static BigDecimal subtotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Quy tắc giá giải cứu: 0 < rescuePrice <= 60% originalPrice (khớp chk_batch_price). */
    public static boolean isValidRescuePrice(BigDecimal originalPrice, BigDecimal rescuePrice) {
        return rescuePrice.signum() > 0 && rescuePrice.compareTo(originalPrice.multiply(MAX_RESCUE_RATIO)) <= 0;
    }

    /** Phần trăm giảm để hiển thị, vd 80.000 → 30.000 là 63 (%). */
    public static int discountPercent(BigDecimal originalPrice, BigDecimal rescuePrice) {
        return BigDecimal.ONE.subtract(rescuePrice.divide(originalPrice, 4, RoundingMode.HALF_UP))
                .multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
