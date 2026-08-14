# 05 — Phase 4: Lifecycle — cancel, timeout, refund

**Goal**: complete the status machine with guarded transitions that are safe under
concurrency (e.g. user cancels at the same moment the timeout job fires).

## Files

| Op | Path |
|----|------|
| N | `backend/src/main/java/com/example/demo/order/OrderTimeoutJob.java` |
| M | `backend/src/main/java/com/example/demo/order/OrderRepository.java` |
| M | `backend/src/main/java/com/example/demo/order/OrderService.java` |
| M | `backend/src/main/java/com/example/demo/order/OrderController.java` |
| M | `backend/src/main/java/com/example/demo/payment/PaymentService.java` |
| T | `backend/src/test/java/com/example/demo/order/OrderLifecycleTest.java` |

## Details

### Guarded transitions (compare-and-set in SQL)

```java
@Modifying
@Query("update Order o set o.status = :to, o.closedAt = :now " +
       "where o.orderNo = :orderNo and o.status = :from")
int closeIfPending(@Param("orderNo") String orderNo,
                   @Param("from") OrderStatus from, @Param("to") OrderStatus to,
                   @Param("now") Instant now);
```

Same pattern for `paidIfPending` / `refundedIfPaid`. Affected rows == 1 → we won the
transition and own the side effects; == 0 → someone else did it → no-op.

### `OrderService`

- `cancelOrder(orderNo)`:
  1. `closeIfPending(... PENDING_PAYMENT → CLOSED)`; 0 rows → 409 `ORDER_NOT_PENDING`.
  2. Restore stock per item (`productService.restoreStock`).
  3. `paymentService.closePayment(orderNo)` (guard `CREATED → CLOSED`).
  4. Best-effort `gateway.close(orderNo)` (log failures).
- `closeExpiredOrder(Order o)` — same steps as cancel, called by the job (no 409).
- `refundOrder(orderNo)`:
  1. Order must be `PAID` (guard `PAID → REFUNDED`); else 409 `ORDER_NOT_PAID`.
  2. `gateway.refund(orderNo, amountYuan)`.
  3. `paymentService.refundPayment(orderNo)` (guard `SUCCESS → REFUNDED`).
  4. Per D5: **no stock restore on refund**.
- `regenPayHtml(orderNo)` — already added in phase 3; used by "pay again" button.

### `OrderTimeoutJob`

```java
@Scheduled(fixedDelay = 60_000)   // runs 60s after previous run finishes
public void closeExpiredOrders() {
    // for each expired pending order, inside its own transaction (TransactionTemplate
    // or self-invocation via separate @Service): closeExpiredOrder(order)
}
```

- Query: `findByStatusAndExpiresAtBefore(PENDING_PAYMENT, now)` with
  `@Lock(PESSIMISTIC_WRITE)` on the order rows so a racing notify (which locks the order
  when marking paid) serializes correctly: one side wins, the other sees the new state.
- The job is **re-entrant safe**: the guard makes double-close impossible; stock restore
  happens exactly once.

### `PaymentService`

- `closePayment(orderNo)`: guard `CREATED → CLOSED`.
- `refundPayment(orderNo)`: guard `SUCCESS → REFUNDED`; store refund meta in `notifyRaw` (or log).

### Controller additions

`POST /api/orders/{orderNo}/cancel` → 200 `OrderResponse` | 404/409.
`POST /api/orders/{orderNo}/refund` → 200 `OrderResponse` | 404/409.

## Acceptance criteria

- [ ] Cancel pending order → stock restored exactly once; payment `CLOSED`.
- [ ] Cancel an already-cancelled order → 409, stock not double-restored.
- [ ] Timeout: set `expires_at` to the past via SQL → job closes it within ~60 s, stock restored.
- [ ] Refund a paid order (sandbox) → order + payment `REFUNDED`; stock unchanged.
- [ ] `./mvnw -q test` green.
