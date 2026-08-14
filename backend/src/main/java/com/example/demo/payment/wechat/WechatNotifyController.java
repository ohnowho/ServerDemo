package com.example.demo.payment.wechat;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.payment.PaymentService;
import com.wechat.pay.java.service.payments.model.Transaction;

import jakarta.servlet.http.HttpServletRequest;

/**
 * WeChat Pay v3 async notification endpoint. In simulation mode no callbacks
 * arrive, so it always declines. In real mode the request body is verified and
 * decrypted by the official SDK (see WechatGateway.parseNotify) and the trade is
 * completed. WeChat expects the JSON result codes {"code":"SUCCESS"|"FAIL"}.
 */
@RestController
@RequestMapping("/api/payments/wechat")
public class WechatNotifyController {

    private static final Logger log = LoggerFactory.getLogger(WechatNotifyController.class);

    private final WechatGateway wechatGateway;
    private final PaymentService paymentService;

    public WechatNotifyController(WechatGateway wechatGateway, PaymentService paymentService) {
        this.wechatGateway = wechatGateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/notify")
    public String notify(HttpServletRequest request) throws IOException {
        if (wechatGateway.isSimulated()) {
            return "{\"code\":\"FAIL\"}";
        }
        String body = request.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames()).forEach(h -> headers.put(h, request.getHeader(h)));
        try {
            Transaction transaction = wechatGateway.parseNotify(body, headers);
            if (transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
                paymentService.markPaidForOrder(transaction.getOutTradeNo(), transaction.getTransactionId());
                return "{\"code\":\"SUCCESS\"}";
            }
            return "{\"code\":\"FAIL\"}";
        } catch (Exception e) {
            log.error("wechat notify handling failed", e);
            return "{\"code\":\"FAIL\"}";
        }
    }
}
