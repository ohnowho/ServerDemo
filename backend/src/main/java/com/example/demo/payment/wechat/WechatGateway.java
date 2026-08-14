package com.example.demo.payment.wechat;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.demo.config.PaymentProperties;
import com.example.demo.config.WechatProperties;
import com.example.demo.payment.PaymentChannel;
import com.example.demo.payment.gateway.GatewayCreateContext;
import com.example.demo.payment.gateway.GatewayCreateResult;
import com.example.demo.payment.gateway.GatewayException;
import com.example.demo.payment.gateway.GatewayQueryResult;
import com.example.demo.payment.gateway.PaymentGateway;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;

/**
 * WeChat Pay v3 adapter (official SDK, Native pay -> QR code). Runs in simulation
 * mode when merchant credentials are missing or global simulation is enabled.
 */
@Component
public class WechatGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(WechatGateway.class);
    private static final String SIMULATED = "SIMULATED";
    private static final Gson GSON = new Gson();

    private final WechatProperties properties;
    private final PaymentProperties paymentProperties;

    private final NativePayService nativePayService;
    private final RefundService refundService;
    private final NotificationConfig config;

    public WechatGateway(WechatProperties properties, PaymentProperties paymentProperties) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        if (isConfigured()) {
            RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(properties.mchId())
                    .privateKey(properties.privateKey())
                    .merchantSerialNumber(properties.merchantSerialNo())
                    .apiV3Key(properties.apiV3Key())
                    .build();
            this.nativePayService = new NativePayService.Builder().config(config).build();
            this.refundService = new RefundService.Builder().config(config).build();
            this.config = config;
        } else {
            this.nativePayService = null;
            this.refundService = null;
            this.config = null;
        }
    }

    /** Verifies and decrypts a WeChat v3 callback; throws when the signature does not validate. */
    public Transaction parseNotify(String body, Map<String, String> headers) throws GatewayException {
        requireConfigured();
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(headers.get("wechatpay-serial"))
                    .timestamp(headers.get("wechatpay-timestamp"))
                    .nonce(headers.get("wechatpay-nonce"))
                    .signature(headers.get("wechatpay-signature"))
                    .body(body)
                    .build();
            NotificationParser parser = new NotificationParser(config);
            return parser.parse(requestParam, Transaction.class);
        } catch (RuntimeException e) {
            throw new GatewayException("wechat notify verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.WECHAT;
    }

    @Override
    public boolean isSimulated() {
        return paymentProperties.simulationEnabled() || !isConfigured();
    }

    @Override
    public GatewayCreateResult create(GatewayCreateContext context) throws GatewayException {
        if (isSimulated()) {
            return new GatewayCreateResult(SIMULATED);
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.appId());
        request.setMchid(properties.mchId());
        request.setDescription(context.subject());
        request.setOutTradeNo(context.orderNo());
        request.setNotifyUrl(properties.notifyUrl());
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(context.amountCents()));
        request.setAmount(amount);
        try {
            PrepayResponse response = nativePayService.prepay(request);
            JsonObject json = new JsonObject();
            json.addProperty("codeUrl", response.getCodeUrl());
            return new GatewayCreateResult(GSON.toJson(json));
        } catch (RuntimeException e) {
            throw new GatewayException("wechat native pay failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayQueryResult query(String outTradeNo) throws GatewayException {
        requireConfigured();
        try {
            QueryOrderByOutTradeNoRequest query = new QueryOrderByOutTradeNoRequest();
            query.setMchid(properties.mchId());
            query.setOutTradeNo(outTradeNo);
            Transaction transaction = nativePayService.queryOrderByOutTradeNo(query);
            if (transaction == null) {
                return new GatewayQueryResult("NOT_EXIST", null);
            }
            return new GatewayQueryResult(transaction.getTradeState().name(), transaction.getTransactionId());
        } catch (RuntimeException e) {
            throw new GatewayException("wechat query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close(String outTradeNo) {
        if (!isConfigured()) {
            return;
        }
        try {
            CloseOrderRequest close = new CloseOrderRequest();
            close.setMchid(properties.mchId());
            close.setOutTradeNo(outTradeNo);
            nativePayService.closeOrder(close);
        } catch (RuntimeException e) {
            log.warn("wechat close failed for {}", outTradeNo, e);
        }
    }

    @Override
    public void refund(String outTradeNo, long amountCents) throws GatewayException {
        requireConfigured();
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(outTradeNo);
        request.setOutRefundNo("R" + outTradeNo + System.currentTimeMillis());
        AmountReq refundAmount = new AmountReq();
        refundAmount.setRefund(amountCents);
        refundAmount.setTotal(amountCents);
        refundAmount.setCurrency("CNY");
        request.setAmount(refundAmount);
        try {
            refundService.create(request);
        } catch (RuntimeException e) {
            throw new GatewayException("wechat refund failed: " + e.getMessage(), e);
        }
    }

    private boolean isConfigured() {
        return notBlank(properties.mchId()) && notBlank(properties.privateKey())
                && notBlank(properties.merchantSerialNo()) && notBlank(properties.apiV3Key());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new GatewayException("wechat pay is not configured: set WECHAT_MCH_ID, WECHAT_PRIVATE_KEY, WECHAT_MERCHANT_SERIAL_NO and WECHAT_API_V3_KEY");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
