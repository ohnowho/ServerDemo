package com.example.demo.payment.dto;

import java.math.BigDecimal;

import com.example.demo.common.Money;
import com.example.demo.payment.PaymentChannel;
import com.example.demo.payment.PaymentRecord;
import com.example.demo.payment.PaymentStatus;
import com.example.demo.payment.PaymentType;

/** Wire representation of a payment ledger row. */
public record PaymentStatusResponse(
        String paymentNo,
        String orderNo,
        PaymentChannel channel,
        PaymentType type,
        PaymentStatus status,
        String channelTradeNo,
        BigDecimal amount) {

    public static PaymentStatusResponse from(PaymentRecord record) {
        return new PaymentStatusResponse(
                record.getPaymentNo(),
                record.getOrderNo(),
                record.getChannel(),
                record.getType(),
                record.getStatus(),
                record.getChannelTradeNo(),
                Money.centsToYuan(record.getAmountCents()));
    }
}
