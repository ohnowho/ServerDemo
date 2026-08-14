package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** WeChat Pay v3 (APIv3) merchant credentials. Empty values => gateway runs in simulation mode. */
@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(
        String mchId,
        String appId,
        String privateKey,
        String merchantSerialNo,
        String apiV3Key,
        String notifyUrl) {
}
