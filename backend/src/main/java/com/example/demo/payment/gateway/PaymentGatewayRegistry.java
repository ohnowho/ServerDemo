package com.example.demo.payment.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.demo.common.BusinessException;
import com.example.demo.payment.PaymentChannel;

import org.springframework.http.HttpStatus;

/** Resolves a channel to its PaymentGateway implementation (all Spring beans by channel()). */
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentChannel, PaymentGateway> gateways = new EnumMap<>(PaymentChannel.class);

    public PaymentGatewayRegistry(List<PaymentGateway> implementations) {
        implementations.forEach(g -> gateways.put(g.channel(), g));
    }

    public PaymentGateway get(PaymentChannel channel) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CHANNEL",
                    "no gateway for channel " + channel);
        }
        return gateway;
    }
}
