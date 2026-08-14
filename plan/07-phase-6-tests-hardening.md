# 07 — Phase 6: Tests & hardening

**Goal**: automated coverage of the money-critical paths + a manual sandbox walkthrough.

## Automated tests (`backend/src/test/java/com/example/demo/`)

### Unit (Mockito, `spring-boot-starter-webmvc-test` deps)

| File | Cases |
|------|-------|
| `product/ProductServiceTest.java` (from phase 1) | deduct ok / insufficient (0 rows) / adjust negative guard |
| `order/OrderServiceTest.java` | create success (stock deducted, totals computed); insufficient stock → rollback; not-on-sale; validation failures |
| `payment/PaymentServiceTest.java` (from phase 3) | `markPaid` idempotent (replay no-op); notify: bad signature / wrong app_id / amount mismatch rejected; reconcile `TRADE_SUCCESS` on poll |
| `order/OrderLifecycleTest.java` (from phase 4) | cancel restores stock once; double-cancel no-op; refund transition guard; timeout close restores stock |

Gateway is a Mockito mock — no real network in unit tests.

### Integration (`@SpringBootTest`, real PostgreSQL)

- `OrderFlowIntegrationTest`: create order → assert stock decreased & payment record
  exists; mark paid via `handleNotify` (mocked gateway) → order `PAID`; cancel →
  stock restored.
- Concurrency check: product with stock = 1 → two parallel `createOrder` calls (one
  must fail `INSUFFICIENT_STOCK`); stock never negative. Use `CountDownLatch` or
  `@Async` threads. Note: `ddl-auto: update` + Postgres is already configured in yml.
- Notify replay: same params twice → single transition.

### Frontend

- `npm run lint` clean; manual walkthrough below (no test framework installed — out of scope).

## Manual sandbox walkthrough (final acceptance)

Prereqs: sandbox app + keys exported (`ALIPAY_APP_ID`, `ALIPAY_PRIVATE_KEY`,
`ALIPAY_PUBLIC_KEY`), backend on 8080, frontend on 3000.

1. `POST /api/products` ×2 (one with stock 1).
2. Buy product with stock 1 → Alipay page → pay as sandbox buyer → return to `/orders`.
3. Order shows `PAID` within ~10 s (polling path — no tunnel needed).
4. Refund it → `REFUNDED`, stock unchanged.
5. Buy again but don't pay → Cancel → stock restored, order `CLOSED`.
6. Buy, don't pay, `UPDATE orders SET expires_at = now() - interval '1 minute'` →
   within ~60 s the job closes it and restores stock.
7. Tunnel (ngrok/cloudflared) → set `ALIPAY_NOTIFY_URL` to tunnel URL → pay →
   confirm `payment_records.notify_raw` populated and order `PAID` via notify alone.
8. Confirm no real keys in git; `git status` shows only intended files.

## Hardening checklist

- [ ] `AlipayProperties` validation: in non-sandbox mode, fail fast at startup if keys
      are blank (can't accidentally "work" without credentials in prod).
- [ ] Payment/order transitions logged with orderNo + paymentNo (audit trail).
- [ ] Notify endpoint documented as auth-exempt in case Spring Security is added later.
- [ ] Configs read from env (`${ALIPAY_*:}`), `.gitignore` covers any local key files.
- [ ] `README.md` gets a short "run it" section (keys, ports, commands).

## Optional extensions (not in this scope)

- QR pay via `alipay.trade.precreate` (D1 override).
- Spring Security + real login; userId from authenticated principal.
- Idempotency key on `POST /api/orders` to prevent double-click double-orders.
- Stock `version` optimistic locking for admin restock edits.
- Webhook-style event log / outbox for payment events.
- WeChat Pay channel (extend `PaymentChannel`).
