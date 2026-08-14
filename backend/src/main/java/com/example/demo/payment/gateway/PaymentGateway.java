package com.example.demo.payment.gateway;

import com.example.demo.payment.PaymentChannel;

/**
 * One payment channel adapter. Implementations exist for Alipay (official SDK),
 * WeChat Pay (official SDK) and card (simulated PSP). The payment domain talks to
 * this interface only, so channels are interchangeable and order code never sees
 * channel specifics.
 */
public interface PaymentGateway {

    PaymentChannel channel();

    /**
     * Prepares the payment at the channel. Returns an opaque payload the pay page
     * uses to render (Alipay form HTML, WeChat code_url JSON, ...).
     */
    GatewayCreateResult create(GatewayCreateContext context) throws GatewayException;

    /** Current trade state at the channel: SUCCESS / CLOSED / NOT_EXIST / ... */
    GatewayQueryResult query(String outTradeNo) throws GatewayException;

    /** Best-effort close of an unpaid trade; failures are logged, not thrown. */
    void close(String outTradeNo);

    /** Refunds the full amount; throws on failure so the caller can roll back. */
    void refund(String outTradeNo, long amountCents) throws GatewayException;

    /** Whether payments through this channel are completed by the local simulation. */
    boolean isSimulated();
}
