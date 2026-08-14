package com.example.demo.product.dto;

import jakarta.validation.constraints.NotNull;

/** Delta to add to (or subtract from, when negative) the current stock. */
public record AdjustStockRequest(@NotNull Integer delta) {
}
