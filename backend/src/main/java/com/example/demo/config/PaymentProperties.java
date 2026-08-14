package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Payment-domain settings shared by all channels.
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        boolean simulationEnabled,
        int orderTimeoutMinutes,
        String returnUrl) {
}
