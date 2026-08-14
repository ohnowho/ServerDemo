package com.example.demo.payment.alipay;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alipay.api.internal.util.AlipaySignature;
import com.example.demo.common.Money;
import com.example.demo.config.AlipayProperties;
import com.example.demo.payment.PaymentRecord;
import com.example.demo.payment.PaymentService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Alipay async notification endpoint. Intentionally unauthenticated and plain-text:
 * nothing is trusted until the signature, app_id and amount are verified. Returns
 * "success" only after processing so Alipay stops retrying.
 */
@RestController
@RequestMapping("/api/payments/alipay")
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);

    private final PaymentService paymentService;
    private final AlipayProperties properties;

    public AlipayNotifyController(PaymentService paymentService, AlipayProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @PostMapping(path = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> params.put(key, values.length > 0 ? values[0] : ""));
        try {
            if (!AlipaySignature.rsaCheckV1(params, properties.alipayPublicKey(), "UTF-8", "RSA2")) {
                log.warn("alipay notify rejected: signature verification failed");
                return "failure";
            }
            if (!properties.appId().equals(params.get("app_id"))) {
                log.warn("alipay notify rejected: app_id mismatch: {}", params.get("app_id"));
                return "failure";
            }
            String orderNo = params.get("out_trade_no");
            if (orderNo == null) {
                log.warn("alipay notify rejected: missing out_trade_no");
                return "failure";
            }
            PaymentRecord record = paymentService.findLatestPendingPayment(orderNo).orElse(null);
            if (record == null) {
                log.warn("alipay notify rejected: no pending payment for order {}", orderNo);
                return "failure";
            }
            String totalAmount = params.get("total_amount");
            if (totalAmount == null
                    || Money.centsToYuan(record.getAmountCents()).compareTo(new BigDecimal(totalAmount)) != 0) {
                log.warn("alipay notify rejected: amount mismatch for {}", orderNo);
                return "failure";
            }
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                paymentService.markPaidForOrder(orderNo, params.get("trade_no"));
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                paymentService.closePendingPayment(orderNo);
            } else if ("WAIT_BUYER_PAY".equals(tradeStatus)) {
                // still pending - nothing to do
            } else {
                log.warn("alipay notify: unexpected trade_status {} for {}", tradeStatus, orderNo);
            }
            return "success";
        } catch (Exception e) {
            log.error("alipay notify handling failed", e);
            return "failure";
        }
    }
}
