package com.example.demo.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes unpaid orders whose payment deadline passed, restoring stock. Runs every
 * 60s; each order is closed in its own transaction and the compare-and-set guard
 * makes the job safe against concurrent cancels or racing channel notifies.
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderService orderService;

    public OrderTimeoutJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeExpiredOrders() {
        List<Order> expired = orderService.findExpiredPendingOrders();
        if (expired.isEmpty()) {
            return;
        }
        log.info("timeout job: closing {} expired order(s)", expired.size());
        for (Order order : expired) {
            try {
                orderService.closeExpired(order.getOrderNo());
            } catch (Exception e) {
                log.error("timeout job: failed to close order {}", order.getOrderNo(), e);
            }
        }
    }
}
