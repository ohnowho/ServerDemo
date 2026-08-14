package com.example.demo.payment;

import java.time.Instant;

import com.example.demo.common.IdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Payment ledger entry, decoupled from the order domain. An order may have several
 * records (payment attempts + refunds); each row carries the channel and its result.
 */
@Entity
@Table(name = "payment_records", indexes = @Index(name = "idx_payment_order", columnList = "order_no"))
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 45)
    private String paymentNo;

    /** Business order number (not unique: an order may be paid, retried or refunded). */
    @Column(nullable = false, length = 40)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false)
    private long amountCents;

    /** Our trade reference sent to the channel (the order number for this demo). */
    @Column(length = 64)
    private String outTradeNo;

    /** Channel-side transaction id, set on success (alipay trade_no / wechat transaction_id). */
    @Column(length = 64)
    private String channelTradeNo;

    /** Opaque channel payload (e.g. Alipay form HTML / WeChat code_url), for rendering the pay page. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    /** True when the payment was completed through the local simulation instead of a real gateway. */
    @Column(nullable = false)
    private boolean simulated;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant paidAt;

    protected PaymentRecord() {
        // required by JPA
    }

    public PaymentRecord(String orderNo, Long userId, PaymentChannel channel, PaymentType type, long amountCents) {
        this.paymentNo = IdGenerator.paymentNo();
        this.orderNo = orderNo;
        this.userId = userId;
        this.channel = channel;
        this.type = type;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public PaymentChannel getChannel() {
        return channel;
    }

    public PaymentType getType() {
        return type;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getOutTradeNo() {
        return outTradeNo;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
