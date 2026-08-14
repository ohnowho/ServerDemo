package com.example.demo.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.BusinessException;
import com.example.demo.config.PaymentProperties;
import com.example.demo.order.Order;
import com.example.demo.order.OrderRepository;
import com.example.demo.order.OrderStatus;
import com.example.demo.payment.dto.CardRequest;
import com.example.demo.payment.dto.PaymentCreateResult;
import com.example.demo.payment.dto.PaymentStatusResponse;
import com.example.demo.payment.gateway.GatewayCreateContext;
import com.example.demo.payment.gateway.GatewayCreateResult;
import com.example.demo.payment.gateway.GatewayException;
import com.example.demo.payment.gateway.GatewayQueryResult;
import com.example.demo.payment.gateway.PaymentGateway;
import com.example.demo.payment.gateway.PaymentGatewayRegistry;

/**
 * Payment domain: owns the payment ledger and knows nothing about product/stock.
 * It talks to channels only through {@link PaymentGateway} and flips the order's
 * money state through {@link OrderRepository} compare-and-set guards.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties paymentProperties;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          PaymentGatewayRegistry gatewayRegistry, PaymentProperties paymentProperties) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentProperties = paymentProperties;
    }

    /**
     * Creates a payment attempt for an order and prepares it at the chosen channel.
     * Fully decoupled from order creation: an order can be paid via any channel,
     * and the attempt lives entirely in the payment domain.
     */
    @Transactional
    public PaymentCreateResult createPayment(String orderNo, PaymentChannel channel, Long userId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "order not found: " + orderNo));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING",
                    "order is " + order.getStatus() + ", only pending orders can be paid");
        }
        if (order.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_EXPIRED",
                    "order payment deadline passed (" + order.getExpiresAt() + ")");
        }

        PaymentGateway gateway = gatewayRegistry.get(channel);
        PaymentRecord record = new PaymentRecord(orderNo, userId, channel, PaymentType.PAYMENT, order.getTotalCents());
        record.setOutTradeNo(orderNo);
        if (gateway.isSimulated()) {
            record.setSimulated(true);
        } else {
            GatewayCreateResult result = gateway.create(new GatewayCreateContext(
                    orderNo, "order " + orderNo, order.getTotalCents(),
                    null, paymentProperties.returnUrl()));
            record.setPayload(result.payload());
        }
        paymentRepository.save(record);
        log.info("payment {} created for order {} via {}", record.getPaymentNo(), orderNo, channel);
        return new PaymentCreateResult(record.getPaymentNo(), "/api/payments/" + record.getPaymentNo() + "/pay");
    }

    /** Status for polling. Real (non-simulated) CREATED payments are reconciled via the gateway. */
    @Transactional
    public PaymentStatusResponse getStatus(String paymentNo) {
        PaymentRecord record = requireByPaymentNo(paymentNo);
        if (record.getStatus() == PaymentStatus.CREATED && !record.isSimulated()) {
            GatewayQueryResult result = gatewayRegistry.get(record.getChannel()).query(record.getOutTradeNo());
            if (isSuccess(result.tradeStatus())) {
                markPaid(record.getPaymentNo(), record.getOrderNo(), result.channelTradeNo());
            } else if (isClosed(result.tradeStatus())) {
                paymentRepository.closeIfCreated(record.getPaymentNo(), PaymentStatus.CREATED, PaymentStatus.FAILED);
            }
            record = requireByPaymentNo(paymentNo);
        }
        return PaymentStatusResponse.from(record);
    }

    /** Completes a payment that was prepared in simulation mode (dev/test hook). */
    @Transactional
    public PaymentStatusResponse simulatePay(String paymentNo) {
        PaymentRecord record = requireByPaymentNo(paymentNo);
        if (!record.isSimulated()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_SIMULATED",
                    "only simulated payments can be completed locally");
        }
        markPaid(record.getPaymentNo(), record.getOrderNo(), "SIM-" + paymentNo);
        return PaymentStatusResponse.from(requireByPaymentNo(paymentNo));
    }

    /** Completes a card payment (simulated PSP). Validates the card data before charging. */
    @Transactional
    public PaymentStatusResponse completeCard(String paymentNo, CardRequest card) {
        PaymentRecord record = requireByPaymentNo(paymentNo);
        if (record.getChannel() != PaymentChannel.CARD) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_CARD_PAYMENT",
                    "payment " + paymentNo + " is not a card payment");
        }
        if (card.cardNumber() == null || card.cardNumber().length() < 12) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD", "card number is invalid");
        }
        if (card.expiry() == null || !card.expiry().matches("\\d{2}/\\d{2}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD", "expiry must be MM/YY");
        }
        if (card.cvv() == null || !card.cvv().matches("\\d{3,4}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD", "cvv is invalid");
        }
        markPaid(record.getPaymentNo(), record.getOrderNo(), "CARD-" + card.cardNumber().substring(0, 4));
        return PaymentStatusResponse.from(requireByPaymentNo(paymentNo));
    }

    /**
     * Idempotent success: payment CREATED->SUCCESS, order PENDING_PAYMENT->PAID.
     * Either transition may be a no-op on replay (Alipay/WeChat retry callbacks).
     */
    @Transactional
    public boolean markPaid(String paymentNo, String orderNo, String channelTradeNo) {
        int payRows = paymentRepository.markPaidIfCreated(paymentNo, PaymentStatus.CREATED, PaymentStatus.SUCCESS,
                channelTradeNo, Instant.now());
        int orderRows = orderRepository.markPaidIfPending(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                Instant.now());
        if (payRows == 1 || orderRows == 1) {
            log.info("payment {} paid (order {}, channel trade {})", paymentNo, orderNo, channelTradeNo);
        }
        return payRows == 1 || orderRows == 1;
    }

    /** Used by channel callbacks: finds the latest pending attempt for the order and marks it paid. */
    @Transactional
    public boolean markPaidForOrder(String orderNo, String channelTradeNo) {
        return findLatestPendingPayment(orderNo)
                .map(p -> markPaid(p.getPaymentNo(), orderNo, channelTradeNo))
                .orElse(false);
    }

    /** Closes all CREATED attempts of an order (cancel/timeout) and tells the channels. */
    @Transactional
    public void closePayment(String orderNo) {
        for (PaymentRecord record : paymentRepository.findPaymentsByOrderNo(orderNo)) {
            if (record.getStatus() == PaymentStatus.CREATED) {
                int rows = paymentRepository.closeIfCreated(record.getPaymentNo(), PaymentStatus.CREATED, PaymentStatus.CLOSED);
                if (rows == 1) {
                    gatewayRegistry.get(record.getChannel()).close(record.getOutTradeNo());
                    log.info("payment {} closed (order {})", record.getPaymentNo(), orderNo);
                }
            }
        }
    }

    /** Closes the latest pending attempt without contacting the channel (channel already closed it). */
    @Transactional
    public void closePendingPayment(String orderNo) {
        findLatestPendingPayment(orderNo).ifPresent(p ->
                paymentRepository.closeIfCreated(p.getPaymentNo(), PaymentStatus.CREATED, PaymentStatus.FAILED));
    }

    /**
     * Refunds the successful payment via its channel and appends a REFUND ledger row.
     * Gateway failure throws and rolls everything back, leaving the order PAID.
     */
    @Transactional
    public void refundPayment(String orderNo) {
        PaymentRecord paid = paymentRepository.findPaymentsByOrderNo(orderNo).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "NO_SUCCESSFUL_PAYMENT",
                        "no successful payment found for order " + orderNo));
        // simulated payments never reached a real gateway, so refunds are simulated too
        if (!paid.isSimulated()) {
            gatewayRegistry.get(paid.getChannel()).refund(paid.getOutTradeNo(), paid.getAmountCents());
        }
        PaymentRecord refund = new PaymentRecord(orderNo, paid.getUserId(), paid.getChannel(),
                PaymentType.REFUND, paid.getAmountCents());
        refund.setOutTradeNo(paid.getOutTradeNo());
        paymentRepository.save(refund);
        paymentRepository.markPaidIfCreated(refund.getPaymentNo(), PaymentStatus.CREATED, PaymentStatus.SUCCESS,
                "REF-" + paid.getChannelTradeNo(), Instant.now());
        log.info("refund recorded for order {} via {}", orderNo, paid.getChannel());
    }

    public Optional<PaymentRecord> findLatestPendingPayment(String orderNo) {
        return paymentRepository.findPaymentsByOrderNo(orderNo).stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .findFirst();
    }

    public String findLatestPaymentNo(String orderNo) {
        return paymentRepository.findPaymentsByOrderNo(orderNo).stream()
                .findFirst()
                .map(PaymentRecord::getPaymentNo)
                .orElse(null);
    }

    public PaymentRecord requireRecord(String paymentNo) {
        return requireByPaymentNo(paymentNo);
    }

    private PaymentRecord requireByPaymentNo(String paymentNo) {
        return paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                        "payment not found: " + paymentNo));
    }

    private static boolean isSuccess(String tradeStatus) {
        return "SUCCESS".equals(tradeStatus) || "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }

    private static boolean isClosed(String tradeStatus) {
        return "CLOSED".equals(tradeStatus) || "TRADE_CLOSED".equals(tradeStatus);
    }
}
