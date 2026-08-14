package com.example.demo.payment.card;

import org.springframework.stereotype.Component;

import com.example.demo.payment.PaymentChannel;
import com.example.demo.payment.gateway.GatewayCreateContext;
import com.example.demo.payment.gateway.GatewayCreateResult;
import com.example.demo.payment.gateway.GatewayException;
import com.example.demo.payment.gateway.GatewayQueryResult;
import com.example.demo.payment.gateway.PaymentGateway;

/**
 * Card payment adapter. This demo simulates the card-issuer/PSP (Stripe, Adyen,
 * ...) entirely on the server: the pay page collects card details and a local
 * endpoint "processes" them. Swap this class for a real PSP integration without
 * touching the rest of the system.
 */
@Component
public class CardGateway implements PaymentGateway {

    private static final String SIMULATED = "SIMULATED";

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.CARD;
    }

    @Override
    public boolean isSimulated() {
        // card processing is always simulated in this demo (no real PSP configured)
        return true;
    }

    @Override
    public GatewayCreateResult create(GatewayCreateContext context) throws GatewayException {
        return new GatewayCreateResult(SIMULATED);
    }

    @Override
    public GatewayQueryResult query(String outTradeNo) throws GatewayException {
        throw new GatewayException("card channel has no remote query (simulated PSP)");
    }

    @Override
    public void close(String outTradeNo) {
        // nothing to close at a remote PSP in simulation mode
    }

    @Override
    public void refund(String outTradeNo, long amountCents) throws GatewayException {
        // simulated PSP: refund is always accepted
    }
}
