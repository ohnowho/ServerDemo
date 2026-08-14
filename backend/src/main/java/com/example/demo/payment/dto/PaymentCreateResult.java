package com.example.demo.payment.dto;

/** Result of creating a payment: the payment number and the pay page URL to open. */
public record PaymentCreateResult(String paymentNo, String payUrl) {
}
