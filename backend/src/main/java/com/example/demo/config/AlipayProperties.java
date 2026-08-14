package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alipay integration settings. Keys are read from environment variables
 * (ALIPAY_APP_ID etc.) so real credentials are never committed.
 */
@ConfigurationProperties(prefix = "alipay")
public record AlipayProperties(
        boolean sandbox,
        String appId,
        String privateKey,
        String alipayPublicKey,
        String notifyUrl,
        String returnUrl) {
}
