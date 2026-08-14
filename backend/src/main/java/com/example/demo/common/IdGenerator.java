package com.example.demo.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Generates human-readable, roughly unique business IDs (timestamp + random suffix). */
public final class IdGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    /** e.g. 2026081310301512345678 */
    public static String orderNo() {
        return LocalDateTime.now().format(TIMESTAMP) + String.format("%08d", RANDOM.nextInt(100_000_000));
    }

    /** e.g. PAY2026081310301512345678 */
    public static String paymentNo() {
        return "PAY" + orderNo();
    }
}
