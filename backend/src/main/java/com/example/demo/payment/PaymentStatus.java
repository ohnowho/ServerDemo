package com.example.demo.payment;

public enum PaymentStatus {
    /** created, awaiting the buyer to complete the payment */
    CREATED,
    SUCCESS,
    FAILED,
    CLOSED,
    REFUNDED
}
