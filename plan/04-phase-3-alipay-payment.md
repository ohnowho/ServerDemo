# 04 — Phase 3: Multi-channel payment integration

> **REVISED** (multi-channel refactor): this phase is now channel-agnostic. A
> `PaymentGateway` interface + `PaymentGatewayRegistry` back Alipay (official SDK),
> WeChat Pay v3 (official SDK) and card (simulated PSP). `payment.simulation-enabled`
> (default true) makes every channel locally demoable; real gateways activate when
> credentials are set. See [`docs/design.md`](../docs/design.md) §3, §5.3–5.4.

**Goal**: start a payment for an order via any channel, render its pay page, verify
channel callbacks and reconcile status via polling.

## Files

| Op | Path |
|----|------|
| N | `backend/src/main/java/com/example/demo/payment/alipay/AlipayGateway.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentService.java` |
| N | `backend/src/main/java/com/example/demo/payment/PaymentController.java` |
| N | `backend/src/main/java/com/example/demo/payment/dto/PaymentStatusResponse.java` |
| N | `backend/src/main/java/com/example/demo/payment/alipay/AlipayNotifyController.java` |
| M | `backend/src/main/java/com/example/demo/order/OrderService.java` |
| T | `backend/src/test/java/com/example/demo/payment/PaymentServiceTest.java` |

## Details

### `AlipayGateway` (wraps the `AlipayClient` bean)

- `String pagePayHtml(String outTradeNo, BigDecimal amountYuan, String subject)`:
  `AlipayTradePagePayRequest` → `setNotifyUrl`, `setReturnUrl`, `setBizContent`
  (`out_trade_no`, `product_code=FAST_INSTANT_TRADE_PAY`, `total_amount`, `subject`) →
  `client.pageExecute(request).getBody()` (the auto-submit HTML form).
- `String queryStatus(String outTradeNo)`: `AlipayTradeQueryRequest` →
  response `getTradeStatus()` (`WAIT_BUYER_PAY` / `TRADE_SUCCESS` / `TRADE_CLOSED` /
  `TRADE_FINISHED`); on `ALIPAY_TRADE_NOT_EXIST` return `"NOT_EXIST"`.
- `void close(String outTradeNo)` / `String refund(String outTradeNo, BigDecimal amountYuan)`:
  `AlipayTradeCloseRequest` / `AlipayTradeRefundRequest` — best-effort: catch SDK
  exceptions, log, never break the calling transaction.
- Amount conversion at the boundary only: `Money.centsToYuan`.

### `PaymentService`

- `PaymentPayResult createPayment(Order order, String orderNo, long amountCents)`:
  upsert `PaymentRecord(CREATED)` → call gateway → return `{paymentNo, payHtml}`.
- `PaymentStatusResponse getStatus(String paymentNo)`:
  1. Load record; if `SUCCESS/FAILED/CLOSED/REFUNDED` → return as-is.
  2. If `CREATED` → `gateway.queryStatus` and **reconcile**:
     - `TRADE_SUCCESS`/`TRADE_FINISHED` → `markPaid(...)`
     - `TRADE_CLOSED` → payment → `CLOSED`, order stays pending (timeout job handles it)
     - `NOT_EXIST`/`WAIT_BUYER_PAY` → keep `CREATED`
  3. Return `{paymentNo, orderNo, status, alipayTradeNo, amount}`.
- `boolean handleNotify(Map<String,String> params)` — see below.
- `markPaid(orderNo, tradeNo, rawParams)` — **@Transactional**:
  - payment `CREATED → SUCCESS` (guard) + store `alipayTradeNo` + `notifyRaw`;
  - order `PENDING_PAYMENT → PAID` + `paidAt` (guard);
  - any guard failure → log "already processed", no-op (idempotency).
- `closePayment(orderNo)` / `refundPayment(orderNo)` — used by phase 4.

### `AlipayNotifyController` — `POST /api/payments/alipay/notify`

1. Read raw form params: `HttpServletRequest.getParameterMap()` → `LinkedHashMap<String,String>`.
2. Verify: `AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2")`
   → else log + return `failure`.
3. `app_id` == configured appId; `out_trade_no` exists and matches record; `total_amount`
   == record `amountCents` (yuan vs cents compare via `Money`).
4. `trade_status` in `TRADE_SUCCESS`/`TRADE_FINISHED` → `paymentService.handleNotify(params)`.
5. Respond body exactly `success` (any other body → Alipay retries).
   Endpoint is intentionally unauthenticated; nothing here is trusted without step 2–4.

### `OrderService` changes

- `createOrder` returns `CreateOrderResponse{order, payHtml}` where
  `payHtml = paymentService.createPayment(...)`.
- Add `POST /api/orders/{orderNo}/pay` (regenerate pay HTML for a still-pending order —
  Alipay allows re-creating the pay form for the same `out_trade_no`): guard
  `status == PENDING_PAYMENT` else 409 `ORDER_NOT_PENDING`.

## Acceptance criteria

- [ ] Sandbox walkthrough (local): create order → `payHtml` renders → sandbox buyer pays →
      `GET /api/payments/{paymentNo}` (which calls `trade.query`) returns `SUCCESS` and
      order is `PAID`. **This works with zero tunnel setup.**
- [ ] Notify path: with a tunnel exposing port 8080, Alipay async notify arrives →
      verified → order paid → response `success`.
- [ ] Replay: calling `markPaid` twice → single state change (idempotent).
- [ ] Wrong `total_amount` / bad signature in a forged notify → rejected, `failure`.
- [ ] `./mvnw -q test` green.
