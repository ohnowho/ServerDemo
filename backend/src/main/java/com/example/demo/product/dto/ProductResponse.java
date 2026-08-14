package com.example.demo.product.dto;

import java.math.BigDecimal;

import com.example.demo.common.Money;
import com.example.demo.product.Product;
import com.example.demo.product.ProductStatus;

/** Wire representation: price in yuan, not cents. */
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        int stock,
        ProductStatus status) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), Money.centsToYuan(p.getPriceCents()), p.getStock(), p.getStatus());
    }
}
