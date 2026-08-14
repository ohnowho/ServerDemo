# 03 — Phase 2: Order creation (transactional)

> **REVISED** (multi-channel refactor): order creation is now **purely the order domain** —
> it no longer creates a payment record or touches any channel. See
> [`docs/design.md`](../docs/design.md) §5.2; payments are started separately via
> `POST /api/payments` (Phase 4 doc).

**Goal**: `POST /api/orders` atomically deducts stock and persists order + items.

## Files

| Op | Path |
|----|------|
| N | `backend/src/main/java/com/example/demo/order/OrderStatus.java` |
| N | `backend/src/main/java/com/example/demo/order/Order.java` |
| N | `backend/src/main/java/com/example/demo/order/OrderItem.java` |
| N | `backend/src/main/java/com/example/demo/order/OrderRepository.java` |
| N | `backend/src/main/java/com/example/demo/order/OrderService.java` |
| N | `backend/src/main/java/com/example/demo/order/OrderController.java` |
| N | `backend/src/main/java/com/example/demo/order/dto/CreateOrderRequest.java` |
| N | `backend/src/main/java/com/example/demo/order/dto/OrderItemRequest.java` |
| N | `backend/src/main/java/com/example/demo/order/dto/OrderResponse.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentStatus.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentChannel.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentRecord.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentRepository.java` |

## Details

### Entities

- `Order`: `orderNo` (unique), `userId`, `status` (`PENDING_PAYMENT` default),
  `totalCents`, `expiresAt` (`createdAt + orderTimeoutMinutes` from config), timestamps.
  `@OneToMany(cascade=ALL, orphanRemoval=true)` items.
- `OrderItem`: `orderId` FK, `productId`, `productName`, `priceCents`, `quantity`,
  `subtotalCents` — **all snapshotted at order time**.
- `PaymentRecord`: `paymentNo` (unique), `orderNo` (unique — 1:1), `channel` (`ALIPAY`),
  `status` (`CREATED`), `amountCents`, `alipayTradeNo` (nullable), `notifyRaw`
  (`@JdbcTypeCode(SqlTypes.JSON)` on a `String` field; fallback: plain `TEXT` if jsonb
  causes friction with ddl-auto), `createdAt`, `paidAt`.

### Repositories

- `OrderRepository`: `findByOrderNo`, `findByUserIdOrderByCreatedAtDesc`,
  `findByStatusAndExpiresAtBefore` (phase 4 adds locking variant).
- `PaymentRepository`: `findByPaymentNo`, `findByOrderNo`.

### `OrderService.createOrder(CreateOrderRequest)` — single `@Transactional`

1. Validate: `userId` non-null, items non-empty, each `quantity` in `1..99`.
2. For each item:
   - `product = productService.get(productId)` → 404 if missing.
   - `status == OFF_SALE` → 409 `NOT_ON_SALE`.
   - `productService.deductStock(productId, quantity)` → 409 `INSUFFICIENT_STOCK` on 0 rows.
3. Compute `subtotalCents` per item and `totalCents`; all arithmetic in `long`.
4. `orderNo = IdGenerator.orderNo()`, `paymentNo = IdGenerator.paymentNo()`;
   `expiresAt = now + orderTimeoutMinutes`.
5. Save order (+items), save `PaymentRecord(CREATED)`.
6. Return `OrderResponse`; `payHtml` placeholder `null` (phase 3 wires it).

### DTOs

- `CreateOrderRequest`: `Long userId` + `List<OrderItemRequest> items` (Bean Validation).
- `OrderItemRequest`: `Long productId`, `int quantity`.
- `OrderResponse`: `orderNo, userId, status, totalAmount (BigDecimal yuan), expiresAt,
  createdAt, items[{productId, productName, price, quantity, subtotal}], paymentNo`.

### `OrderController`

`POST /api/orders` (201, returns `{order: OrderResponse, payHtml}`), `GET /api/orders?userId=`,
`GET /api/orders/{orderNo}`.

## Acceptance criteria

- [ ] Create order → stock reduced exactly by quantities; DB shows order + items + payment record.
- [ ] Second create beyond remaining stock → 409 `INSUFFICIENT_STOCK` and **no** rows inserted
      (whole transaction rolls back).
- [ ] `GET /api/orders/{orderNo}` returns snapshot prices (change product price after →
      order still shows old price).
- [ ] `./mvnw -q test` green.
