# 00 — Architecture & Contracts

Recap of the target state. Full rationale: [`../docs/design.md`](../docs/design.md).

## Target package layout (backend, `com.example.demo`)

```
com.example.demo
├── DemoApplication.java                  (existing)
├── config
│   ├── CorsConfig.java                   (existing)
│   ├── AlipayProperties.java             @ConfigurationProperties("alipay")
│   ├── AlipayConfig.java                 AlipayClient bean (sandbox/prod gateway)
│   └── SchedulingConfig.java             @EnableScheduling
├── common
│   ├── BusinessException.java            code + message + HttpStatus
│   ├── GlobalExceptionHandler.java       @RestControllerAdvice
│   ├── IdGenerator.java                  orderNo / paymentNo
│   └── Money.java                        cents<->yuan BigDecimal conversion
├── user                                  (existing, untouched)
├── product
│   ├── Product.java / ProductRepository.java / ProductService.java
│   ├── ProductController.java
│   └── dto/CreateProductRequest.java, AdjustStockRequest.java
├── order
│   ├── Order.java / OrderItem.java / OrderStatus.java
│   ├── OrderRepository.java / OrderService.java / OrderController.java
│   ├── OrderTimeoutJob.java
│   └── dto/CreateOrderRequest.java, OrderResponse.java, OrderItemRequest.java
└── payment
    ├── PaymentRecord.java / PaymentStatus.java / PaymentChannel.java
    ├── PaymentRepository.java / PaymentService.java / PaymentController.java
    ├── alipay/AlipayGateway.java
    ├── alipay/AlipayNotifyController.java
    └── dto/PaymentStatusResponse.java
```

## Data model (final)

`products(id, name, price_cents, stock, status)`
`orders(id, order_no UK, user_id, status, total_cents, expires_at, created_at, paid_at, closed_at)`
`order_items(id, order_id FK, product_id, product_name, price_cents, quantity, subtotal_cents)`
`payment_records(id, payment_no UK, order_no UK, channel, status, amount_cents, alipay_trade_no, notify_raw, created_at, paid_at)`

Enums:

- `OrderStatus`: `PENDING_PAYMENT, PAID, CLOSED, REFUNDED`
- `PaymentStatus`: `CREATED, SUCCESS, FAILED, CLOSED, REFUNDED`
- `PaymentChannel`: `ALIPAY`
- `ProductStatus`: `ON_SALE, OFF_SALE`

Transition guards (compare-and-set):

```text
Order:     PENDING_PAYMENT --pay--> PAID        (only once)
           PENDING_PAYMENT --cancel/timeout--> CLOSED   (only once)
           PAID --refund--> REFUNDED                    (only once)
Payment:   CREATED --success--> SUCCESS         (only once)
           CREATED --closed--> CLOSED
           SUCCESS --refunded--> REFUNDED
```

## REST API contract (final)

All bodies JSON (`application/json`) except the Alipay notify endpoint.

| Method | Path | Request | Response | Errors |
|--------|------|---------|----------|--------|
| GET | `/api/products` | — | `Product[]` (ON_SALE only) | — |
| GET | `/api/products/{id}` | — | `Product` | 404 `PRODUCT_NOT_FOUND` |
| POST | `/api/products` | `CreateProductRequest` | `Product` (201) | 400 validation |
| PATCH | `/api/products/{id}/stock` | `AdjustStockRequest {delta}` | `Product` | 404, 409 `NEGATIVE_STOCK` |
| POST | `/api/orders` | `CreateOrderRequest {userId, items[{productId, quantity}]}` | `CreateOrderResponse {order, payHtml}` (201) | 400, 404 `PRODUCT_NOT_FOUND`, 409 `NOT_ON_SALE`, 409 `INSUFFICIENT_STOCK` |
| POST | `/api/orders/{orderNo}/pay` | — | `{payHtml}` | 404, 409 `ORDER_NOT_PENDING` |
| GET | `/api/orders/{orderNo}` | — | `OrderResponse` | 404 |
| GET | `/api/orders?userId=` | — | `OrderResponse[]` | — |
| POST | `/api/orders/{orderNo}/cancel` | — | `OrderResponse` | 404, 409 `ORDER_NOT_PENDING` |
| POST | `/api/orders/{orderNo}/refund` | — | `OrderResponse` | 404, 409 `ORDER_NOT_PAID` |
| GET | `/api/payments/{paymentNo}` | — | `PaymentStatusResponse` | 404 |
| POST | `/api/payments/alipay/notify` | form-urlencoded params | plain text `success` / `failure` | — |

## Error model

```json
{ "code": "INSUFFICIENT_STOCK", "message": "stock 3 is not enough for qty 5" }
```

- `BusinessException(code, message)` → mapped by handler (400 / 404 / 409 per call site).
- `MethodArgumentNotValidException` → 400 with field errors.
- Uncaught → 500 `INTERNAL_ERROR` (logged server-side).

## Cross-cutting rules

1. **Transactions**: order creation = 1 transaction (deduct stock + insert order/items/payment).
   Close/refund = 1 transaction each. Alipay HTTP calls happen **after** commit where possible,
   best-effort on failure (log + let poll/notify reconcile).
2. **Idempotency**: notify replays are safe — transitions only fire on valid current state.
3. **Concurrency**: stock ops are atomic SQL updates (see phase 1); timeout job uses
   `PESSIMISTIC_WRITE` locks.
4. **Money**: `Money.centsToYuan(long)`, `Money.yuanToCents(BigDecimal)`.
5. **IDs**: `IdGenerator.orderNo()` → `yyyyMMddHHmmss` + 8 random digits; `paymentNo()` → `PAY` + same.
6. **Logging**: every status transition logged with order/payment no (SLF4J).
