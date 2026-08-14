package com.example.demo.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.demo.common.Money;
import com.example.demo.order.Order;
import com.example.demo.order.OrderItem;
import com.example.demo.order.OrderStatus;

/** Wire representation of an order; money in yuan, items carry order-time snapshots. */
public record OrderResponse(
        String orderNo,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant createdAt,
        Instant paidAt,
        Instant closedAt,
        String paymentNo,
        List<Item> items) {

    public record Item(Long productId, String productName, BigDecimal price, int quantity, BigDecimal subtotal) {
    }

    public static OrderResponse from(Order order, String paymentNo) {
        List<Item> items = order.getItems().stream()
                .map(OrderResponse::toItem)
                .toList();
        return new OrderResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                Money.centsToYuan(order.getTotalCents()),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getClosedAt(),
                paymentNo,
                items);
    }

    private static Item toItem(OrderItem i) {
        return new Item(
                i.getProductId(),
                i.getProductName(),
                Money.centsToYuan(i.getPriceCents()),
                i.getQuantity(),
                Money.centsToYuan(i.getSubtotalCents()));
    }
}
