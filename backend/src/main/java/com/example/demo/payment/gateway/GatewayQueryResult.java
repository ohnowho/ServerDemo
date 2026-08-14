package com.example.demo.payment.gateway;

/** Current trade state at a channel plus the channel-side transaction id. */
public record GatewayQueryResult(String tradeStatus, String channelTradeNo) {
}
