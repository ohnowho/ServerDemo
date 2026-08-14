package com.example.demo.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create a product. {@code price} is in yuan (converted to cents server-side). */
public record CreateProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Min(0) int stock) {
}
