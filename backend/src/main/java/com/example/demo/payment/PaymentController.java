package com.example.demo.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.payment.dto.CardRequest;
import com.example.demo.payment.dto.PaymentCreateRequest;
import com.example.demo.payment.dto.PaymentCreateResult;
import com.example.demo.payment.dto.PaymentStatusResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Starts a payment for an order via the requested channel. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCreateResult create(@Valid @RequestBody PaymentCreateRequest request) {
        return paymentService.createPayment(request.orderNo(), request.channel(), 1L);
    }

    /** Polled by the pay page / frontend while the buyer is completing the payment. */
    @GetMapping("/{paymentNo}")
    public PaymentStatusResponse getStatus(@PathVariable String paymentNo) {
        return paymentService.getStatus(paymentNo);
    }

    /** Dev/test hook: completes a simulated payment (Alipay/WeChat in simulation mode). */
    @PostMapping("/{paymentNo}/simulate")
    public PaymentStatusResponse simulate(@PathVariable String paymentNo) {
        return paymentService.simulatePay(paymentNo);
    }

    /** Simulated card PSP: validates the card form and completes the payment. */
    @PostMapping("/{paymentNo}/card")
    public PaymentStatusResponse completeCard(@PathVariable String paymentNo, @Valid @RequestBody CardRequest card) {
        return paymentService.completeCard(paymentNo, card);
    }
}
