package com.example.demo.payment.dto;

import com.example.demo.payment.PaymentChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request to create a payment for an order via a chosen channel. */
public record PaymentCreateRequest(
        @NotBlank String orderNo,
        @NotNull PaymentChannel channel) {
}
