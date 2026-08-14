package com.example.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.common.BusinessException;
import com.example.demo.config.PaymentProperties;
import com.example.demo.order.dto.CreateOrderRequest;
import com.example.demo.order.dto.OrderItemRequest;
import com.example.demo.order.dto.OrderResponse;
import com.example.demo.payment.PaymentService;
import com.example.demo.product.Product;
import com.example.demo.product.ProductService;
import com.example.demo.product.ProductStatus;

import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    ProductService productService;
    @Mock
    PaymentService paymentService;
    @Mock
    PaymentProperties paymentProperties;

    @InjectMocks
    OrderService orderService;

    private Product lamp() {
        // mocked so getId() returns a value (a fresh entity has no id yet)
        Product lamp = mock(Product.class);
        lenient().when(lamp.getId()).thenReturn(1L);
        lenient().when(lamp.getName()).thenReturn("Lamp");
        lenient().when(lamp.getPriceCents()).thenReturn(1000L);
        lenient().when(lamp.getStatus()).thenReturn(ProductStatus.ON_SALE);
        return lamp;
    }

    private Order pendingOrder(Product product) {
        Order order = new Order(1L, 2000, 15);
        order.addItems(List.of(new OrderItem(product, 2)));
        return order;
    }

    @Test
    void createOrderDeductsStockAndComputesTotals() {
        Product lamp = lamp();
        when(productService.requireEntity(1L)).thenReturn(lamp);
        when(paymentProperties.orderTimeoutMinutes()).thenReturn(15);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 2))));

        verify(productService).deductStock(1L, 2);
        verify(orderRepository).save(any(Order.class));
        // order creation must NOT touch the payment domain
        verify(paymentService, never()).createPayment(any(), any(), any());
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Lamp");
    }

    @Test
    void createOrderRollsBackWhenStockInsufficient() {
        Product lamp = lamp();
        when(productService.requireEntity(1L)).thenReturn(lamp);
        doThrow(new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "no stock"))
                .when(productService).deductStock(1L, 5);

        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 5)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK"));

        verify(orderRepository, never()).save(any());
        verify(paymentService, never()).createPayment(any(), any(), any());
    }

    @Test
    void createOrderRejectsOffSaleProduct() {
        Product offSale = mock(Product.class);
        when(offSale.getStatus()).thenReturn(ProductStatus.OFF_SALE);
        when(productService.requireEntity(1L)).thenReturn(offSale);

        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 1)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_ON_SALE"));

        verify(productService, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void markOrderPaidIsGuardedAndIdempotent() {
        when(orderRepository.markPaidIfPending(eq("O1"), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.PAID), any(Instant.class))).thenReturn(1, 0);

        assertThat(orderService.markOrderPaid("O1")).isTrue();
        assertThat(orderService.markOrderPaid("O1")).isFalse();
    }

    @Test
    void cancelRestoresStockAndClosesPayments() {
        Order order = pendingOrder(lamp());
        when(orderRepository.closeIfPending(eq(order.getOrderNo()), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CLOSED), any(Instant.class))).thenReturn(1);
        when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
        when(paymentService.findLatestPaymentNo(order.getOrderNo())).thenReturn("PAY1");

        orderService.cancel(order.getOrderNo());

        verify(productService).restoreStock(1L, 2);
        verify(paymentService).closePayment(order.getOrderNo());
    }

    @Test
    void cancelOfNonPendingOrderIsRejected() {
        String orderNo = "O1";
        when(orderRepository.closeIfPending(eq(orderNo), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CLOSED), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancel(orderNo))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_PENDING"));

        verify(productService, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    void timeoutCloseIsIdempotentForAlreadyClosedOrder() {
        Order order = pendingOrder(lamp());
        String orderNo = order.getOrderNo();
        when(orderRepository.closeIfPending(eq(orderNo), eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CLOSED), any(Instant.class))).thenReturn(0);
        when(orderRepository.findByOrderNo(orderNo)).thenReturn(Optional.of(order));
        when(paymentService.findLatestPaymentNo(orderNo)).thenReturn("PAY1");

        OrderResponse response = orderService.closeExpired(orderNo);

        verify(productService, never()).restoreStock(anyLong(), anyInt());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void refundDelegatesMoneyMovementToPaymentService() {
        String orderNo = "O1";
        when(orderRepository.refundIfPaid(eq(orderNo), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDED)))
                .thenReturn(1);
        Order order = pendingOrder(lamp());
        when(orderRepository.findByOrderNo(orderNo)).thenReturn(Optional.of(order));
        when(paymentService.findLatestPaymentNo(orderNo)).thenReturn("PAY1");

        orderService.refund(orderNo);

        verify(paymentService).refundPayment(orderNo);
    }

    @Test
    void refundRejectedWhenNotPaid() {
        String orderNo = "O1";
        when(orderRepository.refundIfPaid(eq(orderNo), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDED)))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.refund(orderNo))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_PAID"));

        verify(paymentService, never()).refundPayment(any());
    }
}
