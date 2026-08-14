"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { getJson, postJson, type Order, type PaymentStatusResponse } from "@/lib/api";

const DEMO_USER_ID = 1;
const POLL_INTERVAL_MS = 2000;
const POLL_MAX_ATTEMPTS = 15;

const STATUS_LABELS: Record<Order["status"], string> = {
  PENDING_PAYMENT: "Pending payment",
  PAID: "Paid",
  CLOSED: "Closed",
  REFUNDED: "Refunded",
};

export default function OrdersPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setOrders(await getJson<Order[]>(`/api/orders?userId=${DEMO_USER_ID}`));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  // Initial load: setState only inside the async callback.
  useEffect(() => {
    let cancelled = false;
    getJson<Order[]>(`/api/orders?userId=${DEMO_USER_ID}`)
      .then((data) => {
        if (!cancelled) setOrders(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // When returning from Alipay (?paid=1&orderNo=...), poll the payment status
  // until it is terminal, then refresh the list.
  useEffect(() => {
    const orderNo = searchParams.get("orderNo");
    if (!searchParams.get("paid") || !orderNo) return;

    let attempts = 0;
    let done = false;

    async function tick() {
      if (done || attempts >= POLL_MAX_ATTEMPTS) return;
      attempts += 1;
      try {
        const snapshot = await getJson<Order[]>(`/api/orders?userId=${DEMO_USER_ID}`);
        const order = snapshot.find((o) => o.orderNo === orderNo);
        if (order?.paymentNo) {
          const payment = await getJson<PaymentStatusResponse>(
            `/api/payments/${order.paymentNo}`,
          );
          if (payment.status !== "CREATED") {
            done = true;
            setOrders(snapshot);
            return;
          }
        } else if (!order) {
          done = true;
          return;
        }
      } catch {
        // transient error - keep polling
      }
      if (attempts >= POLL_MAX_ATTEMPTS) {
        done = true;
        refresh();
      }
    }

    const interval = setInterval(tick, POLL_INTERVAL_MS);
    return () => {
      done = true;
      clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  async function run(action: "cancel" | "refund", order: Order) {
    setBusy(order.orderNo);
    setError(null);
    try {
      await postJson(`/api/orders/${order.orderNo}/${action}`);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  function goToPay(order: Order) {
    router.push(`/pay/${order.orderNo}`);
  }

  return (
    <main className="container">
      <h1>Orders</h1>
      <p className="subtitle">Orders for demo user {DEMO_USER_ID}</p>
      {error && <p className="error-text">{error}</p>}
      {orders.length === 0 && !error && (
        <p className="subtitle">No orders yet — buy something from the shop.</p>
      )}
      {orders.map((order) => (
        <div className="card order-card" key={order.orderNo}>
          <div className="order-header">
            <div>
              <strong className="order-no">{order.orderNo}</strong>
              <span className={`badge badge-${order.status.toLowerCase()}`}>
                {STATUS_LABELS[order.status]}
              </span>
            </div>
            <span className="price">¥{order.totalAmount}</span>
          </div>
          <ul className="order-items">
            {order.items.map((item, idx) => (
              <li key={idx}>
                {item.productName} × {item.quantity}{" "}
                <span className="muted">(¥{item.price} each)</span>
              </li>
            ))}
          </ul>
          <div className="order-meta muted">
            Created {new Date(order.createdAt).toLocaleString()} ·{" "}
            {order.expiresAt ? `expires ${new Date(order.expiresAt).toLocaleString()}` : ""}
          </div>
          <div className="row actions">
            {order.status === "PENDING_PAYMENT" && (
              <>
                <button
                  className="buy-btn"
                  disabled={busy !== null}
                  onClick={() => goToPay(order)}
                >
                  {busy === order.orderNo ? "…" : "Pay"}
                </button>
                <button
                  className="ghost-btn"
                  disabled={busy !== null}
                  onClick={() => run("cancel", order)}
                >
                  {busy === order.orderNo ? "…" : "Cancel"}
                </button>
              </>
            )}
            {order.status === "PAID" && (
              <button
                className="ghost-btn"
                disabled={busy !== null}
                onClick={() => run("refund", order)}
              >
                {busy === order.orderNo ? "…" : "Refund"}
              </button>
            )}
          </div>
        </div>
      ))}
    </main>
  );
}
