package com.example.demo.order.dto;

import java.util.List;

import com.example.demo.order.OrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotEmpty List<@Valid OrderItemRequest> items) {
}
