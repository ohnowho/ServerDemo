package com.example.demo.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.common.BusinessException;
import com.example.demo.config.PaymentProperties;
import com.example.demo.order.Order;
import com.example.demo.order.OrderRepository;
import com.example.demo.order.OrderStatus;
import com.example.demo.payment.dto.CardRequest;
import com.example.demo.payment.dto.PaymentCreateResult;
import com.example.demo.payment.dto.PaymentStatusResponse;
import com.example.demo.payment.gateway.GatewayCreateResult;
import com.example.demo.payment.gateway.GatewayQueryResult;
import com.example.demo.payment.gateway.PaymentGateway;
import com.example.demo.payment.gateway.PaymentGatewayRegistry;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    PaymentGatewayRegistry gatewayRegistry;
    @Mock
    PaymentProperties paymentProperties;

    @InjectMocks
    PaymentService paymentService;

    private PaymentRecord paymentRecord(String orderNo, PaymentChannel channel, boolean simulated) {
        PaymentRecord record = new PaymentRecord(orderNo, 1L, channel, PaymentType.PAYMENT, 1000);
        record.setOutTradeNo(orderNo);
        record.setSimulated(simulated);
        return record;
    }

    @Test
    void createPaymentUsesSimulatedGatewayWithoutChannelCall() {
        Order order = new Order(1L, 1000, 15);
        when(orderRepository.findByOrderNo("O1")).thenReturn(Optional.of(order));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.isSimulated()).thenReturn(true);
        when(gatewayRegistry.get(PaymentChannel.WECHAT)).thenReturn(gateway);

        PaymentCreateResult result = paymentService.createPayment("O1", PaymentChannel.WECHAT, 1L);

        verify(gateway, never()).create(any());
        assertThat(result.payUrl()).contains(result.paymentNo());
    }

    @Test
    void createPaymentCallsRealGatewayWhenConfigured() {
        Order order = new Order(1L, 1000, 15);
        when(orderRepository.findByOrderNo("O1")).thenReturn(Optional.of(order));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.isSimulated()).thenReturn(false);
        when(gateway.create(any())).thenReturn(new GatewayCreateResult("<form/>"));
        when(gatewayRegistry.get(PaymentChannel.ALIPAY)).thenReturn(gateway);

        PaymentCreateResult result = paymentService.createPayment("O1", PaymentChannel.ALIPAY, 1L);

        verify(gateway).create(argThat(ctx -> ctx.orderNo().equals("O1") && ctx.amountCents() == 1000));
        assertThat(result.payUrl()).isNotBlank();
    }

    @Test
    void createPaymentRejectsNonPendingOrder() {
        Order paid = mock(Order.class);
        when(paid.getStatus()).thenReturn(OrderStatus.PAID);
        when(orderRepository.findByOrderNo("O1")).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> paymentService.createPayment("O1", PaymentChannel.CARD, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_PENDING"));
    }

    @Test
    void createPaymentRejectsExpiredOrder() {
        Order expired = new Order(1L, 1000, -5); // expiry in the past
        when(orderRepository.findByOrderNo("O1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> paymentService.createPayment("O1", PaymentChannel.CARD, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_EXPIRED"));
    }

    @Test
    void markPaidTransitionsOnceAndIsIdempotentOnReplay() {
        when(paymentRepository.markPaidIfCreated(eq("PAY1"), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("T1"), any(Instant.class))).thenReturn(1, 0);
        when(orderRepository.markPaidIfPending(eq("O1"), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.PAID), any(Instant.class))).thenReturn(1, 0);

        assertThat(paymentService.markPaid("PAY1", "O1", "T1")).isTrue();
        // channel callback replay: no transition may fire a second time
        assertThat(paymentService.markPaid("PAY1", "O1", "T1")).isFalse();

        verify(paymentRepository, times(2)).markPaidIfCreated(eq("PAY1"), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("T1"), any(Instant.class));
    }

    @Test
    void getStatusReconcilesWithGatewayWhenCreated() {
        PaymentRecord record = paymentRecord("O1", PaymentChannel.ALIPAY, false);
        String payNo = record.getPaymentNo();
        PaymentRecord updated = mock(PaymentRecord.class);
        when(updated.getPaymentNo()).thenReturn(payNo);
        when(updated.getOrderNo()).thenReturn("O1");
        when(updated.getChannel()).thenReturn(PaymentChannel.ALIPAY);
        when(updated.getType()).thenReturn(PaymentType.PAYMENT);
        when(updated.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        when(updated.getChannelTradeNo()).thenReturn("T1");
        when(updated.getAmountCents()).thenReturn(1000L);

        when(paymentRepository.findByPaymentNo(payNo)).thenReturn(Optional.of(record), Optional.of(updated));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.query("O1")).thenReturn(new GatewayQueryResult("TRADE_SUCCESS", "T1"));
        when(gatewayRegistry.get(PaymentChannel.ALIPAY)).thenReturn(gateway);
        when(paymentRepository.markPaidIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("T1"), any(Instant.class))).thenReturn(1);
        when(orderRepository.markPaidIfPending(eq("O1"), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.PAID), any(Instant.class))).thenReturn(1);

        PaymentStatusResponse response = paymentService.getStatus(payNo);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.channelTradeNo()).isEqualTo("T1");
    }

    @Test
    void simulatePayCompletesOnlySimulatedPayments() {
        PaymentRecord simulated = paymentRecord("O1", PaymentChannel.WECHAT, true);
        String payNo = simulated.getPaymentNo();
        when(paymentRepository.findByPaymentNo(payNo)).thenReturn(Optional.of(simulated));
        when(paymentRepository.markPaidIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("SIM-" + payNo), any(Instant.class))).thenReturn(1);
        when(orderRepository.markPaidIfPending(eq("O1"), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.PAID), any(Instant.class))).thenReturn(1);

        paymentService.simulatePay(payNo);

        verify(paymentRepository).markPaidIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("SIM-" + payNo), any(Instant.class));
    }

    @Test
    void simulatePayRejectsRealPayment() {
        PaymentRecord real = paymentRecord("O1", PaymentChannel.ALIPAY, false);
        when(paymentRepository.findByPaymentNo(real.getPaymentNo())).thenReturn(Optional.of(real));

        assertThatThrownBy(() -> paymentService.simulatePay(real.getPaymentNo()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_SIMULATED"));
    }

    @Test
    void completeCardValidatesAndCompletes() {
        PaymentRecord card = paymentRecord("O1", PaymentChannel.CARD, true);
        String payNo = card.getPaymentNo();
        when(paymentRepository.findByPaymentNo(payNo)).thenReturn(Optional.of(card));
        when(paymentRepository.markPaidIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("CARD-4111"), any(Instant.class))).thenReturn(1);
        when(orderRepository.markPaidIfPending(eq("O1"), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.PAID), any(Instant.class))).thenReturn(1);

        paymentService.completeCard(payNo, new CardRequest("4111111111111111", "ZHANG SAN", "08/29", "123"));

        verify(paymentRepository).markPaidIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), eq("CARD-4111"), any(Instant.class));
    }

    @Test
    void completeCardRejectsInvalidCardNumber() {
        PaymentRecord card = paymentRecord("O1", PaymentChannel.CARD, true);
        when(paymentRepository.findByPaymentNo(card.getPaymentNo())).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> paymentService.completeCard(card.getPaymentNo(),
                new CardRequest("123", "ZHANG SAN", "08/29", "123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_CARD"));
    }

    @Test
    void closePaymentClosesCreatedAttemptsAndTellsGateway() {
        PaymentRecord created = paymentRecord("O1", PaymentChannel.ALIPAY, true);
        String payNo = created.getPaymentNo();
        when(paymentRepository.findPaymentsByOrderNo("O1")).thenReturn(List.of(created));
        when(paymentRepository.closeIfCreated(eq(payNo), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.CLOSED))).thenReturn(1);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gatewayRegistry.get(PaymentChannel.ALIPAY)).thenReturn(gateway);

        paymentService.closePayment("O1");

        verify(gateway).close("O1");
    }

    @Test
    void refundPaymentRefundsViaGatewayAndRecordsRefundRow() {
        PaymentRecord paid = mock(PaymentRecord.class);
        when(paid.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        when(paid.getChannel()).thenReturn(PaymentChannel.ALIPAY);
        when(paid.getOutTradeNo()).thenReturn("O1");
        when(paid.getAmountCents()).thenReturn(1000L);
        when(paid.getUserId()).thenReturn(1L);
        when(paid.getChannelTradeNo()).thenReturn("T1");
        when(paymentRepository.findPaymentsByOrderNo("O1")).thenReturn(List.of(paid));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gatewayRegistry.get(PaymentChannel.ALIPAY)).thenReturn(gateway);
        when(paymentRepository.markPaidIfCreated(anyString(), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), anyString(), any(Instant.class))).thenReturn(1);

        paymentService.refundPayment("O1");

        verify(gateway).refund("O1", 1000);
        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PaymentType.REFUND);
        assertThat(captor.getValue().getChannel()).isEqualTo(PaymentChannel.ALIPAY);
        assertThat(captor.getValue().getAmountCents()).isEqualTo(1000);
    }

    @Test
    void refundPaymentSkipsGatewayForSimulatedPayments() {
        PaymentRecord paid = mock(PaymentRecord.class);
        when(paid.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        when(paid.isSimulated()).thenReturn(true);
        when(paid.getChannel()).thenReturn(PaymentChannel.WECHAT);
        when(paid.getOutTradeNo()).thenReturn("O1");
        when(paid.getAmountCents()).thenReturn(1000L);
        when(paid.getUserId()).thenReturn(1L);
        when(paid.getChannelTradeNo()).thenReturn("SIM-1");
        when(paymentRepository.findPaymentsByOrderNo("O1")).thenReturn(List.of(paid));
        when(paymentRepository.markPaidIfCreated(anyString(), eq(PaymentStatus.CREATED),
                eq(PaymentStatus.SUCCESS), anyString(), any(Instant.class))).thenReturn(1);

        paymentService.refundPayment("O1");

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PaymentType.REFUND);
        assertThat(captor.getValue().getChannel()).isEqualTo(PaymentChannel.WECHAT);
    }

    @Test
    void refundPaymentRejectedWhenNoSuccessfulPayment() {
        PaymentRecord created = paymentRecord("O1", PaymentChannel.CARD, true);
        when(paymentRepository.findPaymentsByOrderNo("O1")).thenReturn(List.of(created));

        assertThatThrownBy(() -> paymentService.refundPayment("O1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NO_SUCCESSFUL_PAYMENT"));
    }
}
