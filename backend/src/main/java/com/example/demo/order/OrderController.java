package com.example.demo.order;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.order.dto.CreateOrderRequest;
import com.example.demo.order.dto.OrderResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Creates the order only; payment is started separately via POST /api/payments. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(@PathVariable String orderNo) {
        return orderService.get(orderNo);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam Long userId) {
        return orderService.listByUser(userId);
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderResponse cancel(@PathVariable String orderNo) {
        return orderService.cancel(orderNo);
    }

    @PostMapping("/{orderNo}/refund")
    public OrderResponse refund(@PathVariable String orderNo) {
        return orderService.refund(orderNo);
    }
}
