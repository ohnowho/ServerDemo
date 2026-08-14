# Execution Plan — Order · Stock · Multi-Channel Payment

Detailed, phase-by-phase implementation plan. High-level design rationale lives in
[`docs/design.md`](../docs/design.md) — this folder is the executable version.

## Document index

| Doc | Contents | Status |
|-----|----------|--------|
| [00-architecture.md](00-architecture.md) | Package layout, data model, API contract, cross-cutting rules | ✅ updated (multi-channel) |
| [01-phase-0-foundation.md](01-phase-0-foundation.md) | Alipay SDK, config, error handling, scheduling | ✅ built |
| [02-phase-1-product-stock.md](02-phase-1-product-stock.md) | Product CRUD + atomic stock operations | ✅ built |
| [03-phase-2-order.md](03-phase-2-order.md) | Transactional order creation, **payment decoupled** | ✅ built |
| [04-phase-3-alipay-payment.md](04-phase-3-alipay-payment.md) | ~~Alipay only~~ → **multi-channel**: Alipay + WeChat + card, simulation mode | ✅ rebuilt |
| [05-phase-4-lifecycle.md](05-phase-4-lifecycle.md) | Cancel, timeout job, refund | ✅ built |
| [06-phase-5-frontend.md](06-phase-5-frontend.md) | Shop, checkout with channel choice, orders, polling | ✅ built |
| [07-phase-6-tests-hardening.md](07-phase-6-tests-hardening.md) | 31 unit tests; live E2E smoke; sandbox walkthrough pending keys | ✅ done |

**Legend**: `N` = new file, `M` = modify existing file, `T` = test file.

## Architecture decisions (adopted)

1. **Orders and payments are separate domains.** `POST /api/orders` only deducts stock
   and creates the order; `POST /api/payments {orderNo, channel}` starts a payment via
   any channel. Payment owns the ledger; orders only expose guarded state transitions
   (markPaid / close / refund) that the payment domain drives.
2. **Multi-channel via one interface.** `PaymentGateway` (create / query / close /
   refund / isSimulated) with `AlipayGateway` (official SDK), `WechatGateway` (official
   wechatpay-java v3, Native pay) and `CardGateway` (simulated PSP). A
   `PaymentGatewayRegistry` maps channel → adapter.
3. **Simulation mode is the default** (`payment.simulation-enabled: true`): channels
   without credentials render a local pay page with a "simulate pay" button, so the
   entire flow is demoable with zero keys. Real gateways switch on by removing the flag
   and setting `ALIPAY_*` / `WECHAT_*` env vars.

## Global rules (apply to every phase)

1. All money arithmetic in cents (`long`); convert only at API boundary.
2. Every status transition is guarded: compare-and-set style, idempotent.
3. Channel SDKs never leak into order code; params passed as plain data.
4. No real keys committed; everything overridable via env vars.
5. Compile-check after each phase: `cd backend && ./mvnw -q compile`.

## Definition of done

- Phase acceptance criteria pass (see each phase doc).
- `./mvnw test` green (31 tests).
- End-to-end smoke: create → pay (card / WeChat / Alipay, simulated) → refund →
  timeout restore, verified in the browser.
