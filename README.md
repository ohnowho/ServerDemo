# ServerDemo

Order / stock / **multi-channel payment** system (Alipay + WeChat + credit card).
Design: [`docs/design.md`](docs/design.md) · execution plan: [`plan/`](plan/).

## Stack

- **Backend**: Spring Boot 4.1 (Java 21), JPA/Hibernate, PostgreSQL
- **Channels**: Alipay SDK `com.alipay.sdk:alipay-sdk-java` 4.40.272.ALL ·
  WeChat Pay v3 `com.github.wechatpay-apiv3:wechatpay-java` 0.2.17 ·
  card (simulated PSP)
- **Frontend**: Next.js 16 / React 19 (TypeScript)

## Run it

Prerequisites: PostgreSQL on `localhost:5432` (db `demo`, user `konan`), Java 21, Node 20+.

```sh
# backend (port 8080)
cd backend && ./mvnw spring-boot:run

# frontend (port 3000)
cd frontend && npm install && npm run dev
```

Open http://localhost:3000 — Shop, checkout (choose Alipay / WeChat Pay / credit card), Orders.

## Simulation mode (default)

`payment.simulation-enabled: true` — no credentials needed. Every channel renders a
local pay page with a **Simulate successful payment** button (or a card form). Full
order → pay → refund loop works locally.

To use the real gateways, set the flag to `false` and export credentials:

```sh
export ALIPAY_APP_ID=...            # sandbox keys from open.alipay.com work
export ALIPAY_PRIVATE_KEY='-----BEGIN RSA PRIVATE KEY-----...'
export ALIPAY_PUBLIC_KEY='-----BEGIN PUBLIC KEY-----...'

export WECHAT_MCH_ID=...            # APIv3 merchant account
export WECHAT_APP_ID=...
export WECHAT_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----...'
export WECHAT_MERCHANT_SERIAL_NO=...
export WECHAT_API_V3_KEY=...
```

Async channel notifies cannot reach `localhost` — use a tunnel for callback testing;
the polling + channel-query path works locally regardless.

## API surface

- Products: `GET/POST /api/products`, `PATCH /api/products/{id}/stock`
- Orders (no payment): `POST /api/orders`, `GET /api/orders?userId=`,
  `GET /api/orders/{orderNo}`, `POST /api/orders/{orderNo}/{cancel|refund}`
- Payments (channel-agnostic): `POST /api/payments` `{orderNo, channel}`,
  `GET /api/payments/{paymentNo}`, `GET /api/payments/{paymentNo}/pay`,
  `POST /api/payments/{paymentNo}/{simulate|card}`
- Channel callbacks: `POST /api/payments/alipay/notify`, `POST /api/payments/wechat/notify`
