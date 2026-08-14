package com.example.demo.payment.alipay;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.example.demo.common.Money;
import com.example.demo.config.AlipayProperties;
import com.example.demo.config.PaymentProperties;
import com.example.demo.payment.PaymentChannel;
import com.example.demo.payment.gateway.GatewayCreateContext;
import com.example.demo.payment.gateway.GatewayCreateResult;
import com.example.demo.payment.gateway.GatewayException;
import com.example.demo.payment.gateway.GatewayQueryResult;
import com.example.demo.payment.gateway.PaymentGateway;

/**
 * Alipay adapter (official SDK, public-key mode, RSA2). Runs in simulation mode
 * when credentials are missing or global simulation is enabled, so the whole
 * flow can be demoed locally without sandbox keys.
 */
@Component
public class AlipayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(AlipayGateway.class);
    private static final String SIMULATED = "SIMULATED";

    private final AlipayClient client;
    private final AlipayProperties properties;
    private final PaymentProperties paymentProperties;

    public AlipayGateway(AlipayClient client, AlipayProperties properties, PaymentProperties paymentProperties) {
        this.client = client;
        this.properties = properties;
        this.paymentProperties = paymentProperties;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.ALIPAY;
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
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.notifyUrl());
        request.setReturnUrl(paymentProperties.returnUrl());
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(context.orderNo());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTotalAmount(Money.centsToYuan(context.amountCents()).toPlainString());
        model.setSubject(context.subject());
        request.setBizModel(model);
        try {
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                throw new GatewayException("alipay page pay failed: " + response.getSubCode() + " " + response.getSubMsg());
            }
            return new GatewayCreateResult(response.getBody());
        } catch (AlipayApiException e) {
            throw new GatewayException("alipay page pay failed: " + e.getErrMsg(), e);
        }
    }

    @Override
    public GatewayQueryResult query(String outTradeNo) throws GatewayException {
        requireConfigured();
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    return new GatewayQueryResult("NOT_EXIST", null);
                }
                throw new GatewayException("alipay query failed: " + response.getSubCode() + " " + response.getSubMsg());
            }
            return new GatewayQueryResult(response.getTradeStatus(), response.getTradeNo());
        } catch (AlipayApiException e) {
            throw new GatewayException("alipay query failed: " + e.getErrMsg(), e);
        }
    }

    @Override
    public void close(String outTradeNo) {
        if (!isConfigured()) {
            return;
        }
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (!response.isSuccess()) {
                log.warn("alipay close for {} not confirmed: {} {}", outTradeNo, response.getSubCode(), response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.warn("alipay close failed for {}", outTradeNo, e);
        }
    }

    @Override
    public void refund(String outTradeNo, long amountCents) throws GatewayException {
        requireConfigured();
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(outTradeNo);
        model.setRefundAmount(Money.centsToYuan(amountCents).toPlainString());
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (!response.isSuccess()) {
                throw new GatewayException("alipay refund failed: " + response.getSubCode() + " " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new GatewayException("alipay refund failed: " + e.getErrMsg(), e);
        }
    }

    private boolean isConfigured() {
        return notBlank(properties.appId()) && notBlank(properties.privateKey()) && notBlank(properties.alipayPublicKey());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new GatewayException("alipay is not configured: set ALIPAY_APP_ID, ALIPAY_PRIVATE_KEY and ALIPAY_PUBLIC_KEY");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
