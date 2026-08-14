package com.example.demo.common;

import java.math.BigDecimal;

/**
 * Money conversions. Everything is stored as cents (long); BigDecimal yuan
 * (scale 2) is used only at the API boundary.
 */
public final class Money {

    private Money() {
    }

    /** 12345 -> 123.45 */
    public static BigDecimal centsToYuan(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    /** 123.45 -> 12345; throws ArithmeticException on non-whole-cent input */
    public static long yuanToCents(BigDecimal yuan) {
        return yuan.movePointRight(2).longValueExact();
    }
}
