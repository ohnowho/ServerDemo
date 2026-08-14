package com.example.demo.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.BusinessException;
import com.example.demo.config.PaymentProperties;
import com.example.demo.order.dto.CreateOrderRequest;
import com.example.demo.order.dto.OrderItemRequest;
import com.example.demo.order.dto.OrderResponse;
import com.example.demo.payment.PaymentService;
import com.example.demo.product.Product;
import com.example.demo.product.ProductService;
import com.example.demo.product.ProductStatus;

/**
 * Order domain: pure commerce (stock, totals, lifecycle). It knows nothing about
 * payment channels — money moves happen through {@link PaymentService}, which owns
 * the ledger and reports back via the guarded status transitions below.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    public OrderService(OrderRepository orderRepository, ProductService productService,
                        PaymentService paymentService, PaymentProperties paymentProperties) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
    }

    /**
     * One transaction: validate -> atomically deduct stock -> insert order + items.
     * Payment is NOT created here; the client calls POST /api/payments with the
     * channel of its choice afterwards.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = new ArrayList<>();
        long totalCents = 0;
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productService.requireEntity(itemRequest.productId());
            if (product.getStatus() != ProductStatus.ON_SALE) {
                throw new BusinessException(HttpStatus.CONFLICT, "NOT_ON_SALE",
                        "product " + product.getName() + " is not on sale");
            }
            productService.deductStock(product.getId(), itemRequest.quantity());
            long subtotal = product.getPriceCents() * (long) itemRequest.quantity();
            totalCents = Math.addExact(totalCents, subtotal);
            items.add(new OrderItem(product, itemRequest.quantity()));
        }
        Order order = new Order(request.userId(), totalCents, paymentProperties.orderTimeoutMinutes());
        order.addItems(items);
        orderRepository.save(order);
        log.info("order {} created for user {} ({} cents)", order.getOrderNo(), request.userId(), totalCents);
        return OrderResponse.from(order, null);
    }

    /** Called by the payment domain when a payment succeeds. Guarded + idempotent. */
    @Transactional
    public boolean markOrderPaid(String orderNo) {
        int rows = orderRepository.markPaidIfPending(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                Instant.now());
        if (rows == 1) {
            log.info("order {} marked PAID", orderNo);
        }
        return rows == 1;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> OrderResponse.from(o, paymentService.findLatestPaymentNo(o.getOrderNo())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String orderNo) {
        return OrderResponse.from(require(orderNo), paymentService.findLatestPaymentNo(orderNo));
    }

    @Transactional
    public OrderResponse cancel(String orderNo) {
        int rows = orderRepository.closeIfPending(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.CLOSED,
                Instant.now());
        if (rows == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING",
                    "only pending orders can be cancelled");
        }
        Order order = require(orderNo);
        restoreStock(order);
        paymentService.closePayment(orderNo);
        log.info("order {} cancelled by user", orderNo);
        return OrderResponse.from(order, paymentService.findLatestPaymentNo(orderNo));
    }

    /** Runs in its own transaction so the pessimistic locks on expired rows are acquired properly. */
    @Transactional
    public List<Order> findExpiredPendingOrders() {
        return orderRepository.findExpiredPending(OrderStatus.PENDING_PAYMENT, Instant.now());
    }

    /** Used by the timeout job; idempotent so re-runs and racing notifies are safe. */
    @Transactional
    public OrderResponse closeExpired(String orderNo) {
        int rows = orderRepository.closeIfPending(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.CLOSED,
                Instant.now());
        if (rows == 0) {
            return get(orderNo); // someone else already closed/paid it
        }
        Order order = require(orderNo);
        restoreStock(order);
        paymentService.closePayment(orderNo);
        log.info("order {} auto-closed (payment timeout)", orderNo);
        return OrderResponse.from(order, paymentService.findLatestPaymentNo(orderNo));
    }

    /** Order-side guard; the actual money movement (gateway refund + ledger) lives in PaymentService. */
    @Transactional
    public OrderResponse refund(String orderNo) {
        int rows = orderRepository.refundIfPaid(orderNo, OrderStatus.PAID, OrderStatus.REFUNDED);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_NOT_PAID",
                    "only paid orders can be refunded");
        }
        paymentService.refundPayment(orderNo);
        log.info("order {} refunded", orderNo);
        return get(orderNo);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            productService.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    private Order require(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "order not found: " + orderNo));
    }
}
