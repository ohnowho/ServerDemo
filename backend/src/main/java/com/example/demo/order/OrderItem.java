package com.example.demo.order;

import com.example.demo.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One purchased line. Name and price are snapshotted at order time. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long subtotalCents;

    protected OrderItem() {
        // required by JPA
    }

    public OrderItem(Product product, int quantity) {
        this.productId = product.getId();
        this.productName = product.getName();
        this.priceCents = product.getPriceCents();
        this.quantity = quantity;
        this.subtotalCents = product.getPriceCents() * (long) quantity;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }
}
