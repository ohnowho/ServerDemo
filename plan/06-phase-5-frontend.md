# 06 — Phase 5: Frontend (Next.js)

**Goal**: minimal UI to exercise the whole flow — product grid → buy → Alipay → back →
poll → order list with cancel/refund. Demo user id is hardcoded (`1`).

## Files

| Op | Path |
|----|------|
| N | `frontend/lib/api.ts` |
| M | `frontend/app/page.tsx` |
| N | `frontend/app/orders/page.tsx` |
| M | `frontend/app/globals.css` (minor: cards/badges/buttons) |
| M | `frontend/app/layout.tsx` (nav link to /orders) |

## Details

### `lib/api.ts`

```ts
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
export async function getJson<T>(path: string): Promise<T>;
export async function postJson<T>(path: string, body?: unknown): Promise<T>; // 4xx -> throw {code, message}
export function submitPayHtml(html: string): void; // hidden form injection + submit
```

### `/` — product grid

- Client component; `useEffect` → `GET /api/products`.
- Each card: name, price (¥, from cents/100), stock, quantity stepper (1..stock), Buy.
- Buy → `POST /api/orders {userId: 1, items: [{productId, quantity}]}` → response has
  `payHtml` → `submitPayHtml` (auto-submits to Alipay in same tab; `return_url` brings
  the user back).
- Insufficient-stock / errors surface as inline alert text.

### `/orders` — order list

- `GET /api/orders?userId=1` on mount; rows: orderNo (short), created time, status
  badge (pending=amber, paid=green, closed=grey, refunded=blue), amount ¥.
- Actions per status:
  - `PENDING_PAYMENT`: **Pay** → `POST /api/orders/{orderNo}/pay` → `submitPayHtml`;
    **Cancel** → `POST .../cancel` → refetch.
  - `PAID`: **Refund** → `POST .../refund` → refetch.
- Accept `?paid=1&orderNo=` (from `return_url`) → poll
  `GET /api/payments/{paymentNo}` — orderNo is unique per order but paymentNo differs,
  so order list response must include `paymentNo` (already in `OrderResponse`) →
  poll every 2 s up to 15 tries → refetch when terminal.

### Wiring for `return_url`

`AlipayProperties.returnUrl` = `http://localhost:3000/orders` (from yml). After Alipay
redirects, the orders page sees the query params and starts polling.

## Acceptance criteria

- [ ] `npm run dev` → product grid renders from backend.
- [ ] Buy → lands on Alipay sandbox login/pay page.
- [ ] Pay with sandbox buyer → redirected back to `/orders` → row flips to `PAID`
      within ~10 s (polling + `trade.query`).
- [ ] Cancel/Refund buttons work and update status immediately.
- [ ] `npm run lint` clean.
