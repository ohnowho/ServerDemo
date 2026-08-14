package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;

/**
 * Builds the Alipay SDK client in public-key mode (RSA2). The gateway URL
 * switches automatically between the sandbox and production environments.
 */
@Configuration
public class AlipayConfig {

    private static final String SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final String PROD_GATEWAY = "https://openapi.alipay.com/gateway.do";

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) {
        String gateway = properties.sandbox() ? SANDBOX_GATEWAY : PROD_GATEWAY;
        return new DefaultAlipayClient(
                gateway,
                properties.appId(),
                properties.privateKey(),
                "json",
                "UTF-8",
                properties.alipayPublicKey(),
                "RSA2");
    }
}
