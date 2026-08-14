package com.example.demo.payment.gateway;

/** Input for creating a payment at a channel. All fields are channel-agnostic. */
public record GatewayCreateContext(
        String orderNo,
        String subject,
        long amountCents,
        String notifyUrl,
        String returnUrl) {
}
