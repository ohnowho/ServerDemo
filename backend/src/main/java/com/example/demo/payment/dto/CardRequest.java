package com.example.demo.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Card details for the simulated card PSP. */
public record CardRequest(
        @NotBlank String cardNumber,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{2}/\\d{2}") String expiry,
        @NotBlank @Pattern(regexp = "\\d{3,4}") String cvv) {
}
