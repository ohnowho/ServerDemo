"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  API_BASE,
  getJson,
  postJson,
  type Order,
  type PaymentChannel,
  type PaymentCreateResult,
} from "@/lib/api";

const CHANNELS: { channel: PaymentChannel; label: string; hint: string }[] = [
  { channel: "ALIPAY", label: "Alipay", hint: "Alipay sandbox" },
  { channel: "WECHAT", label: "WeChat Pay", hint: "WeChat Pay v3" },
  { channel: "CARD", label: "Credit card", hint: "Simulated PSP" },
];

const STATUS_LABELS: Record<Order["status"], string> = {
  PENDING_PAYMENT: "Pending payment",
  PAID: "Paid",
  CLOSED: "Closed",
  REFUNDED: "Refunded",
};

export default function PayPage() {
  const params = useParams<{ orderNo: string }>();
  const router = useRouter();
  const orderNo = params.orderNo;
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<PaymentChannel | null>(null);

  useEffect(() => {
    let cancelled = false;
    getJson<Order>(`/api/orders/${orderNo}`)
      .then((o) => {
        if (!cancelled) setOrder(o);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [orderNo]);

  async function pay(channel: PaymentChannel) {
    setBusy(channel);
    setError(null);
    try {
      const result = await postJson<PaymentCreateResult>("/api/payments", {
        orderNo,
        channel,
      });
      // the pay page is rendered by the backend (channel-specific HTML)
      router.push(`${API_BASE}${result.payUrl}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="container">
      <h1>Checkout</h1>
      {error && <p className="error-text">{error}</p>}
      {!order && !error && <p className="subtitle">Loading…</p>}
      {order && (
        <>
          <p className="subtitle">
            Order <span className="order-no">{order.orderNo}</span> · Status{" "}
            <span className={`badge badge-${order.status.toLowerCase()}`}>{STATUS_LABELS[order.status]}</span>
          </p>
          {order.status === "PENDING_PAYMENT" ? (
            <div className="grid">
              {CHANNELS.map(({ channel, label, hint }) => (
                <div className="card product-card" key={channel}>
                  <h2>{label}</h2>
                  <p className="subtitle">{hint}</p>
                  <p className="price">¥{order.totalAmount}</p>
                  <button
                    className="buy-btn"
                    disabled={busy !== null}
                    onClick={() => pay(channel)}
                  >
                    {busy === channel ? "Processing…" : `Pay with ${label}`}
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <p className="subtitle">
              This order cannot be paid ({STATUS_LABELS[order.status]}).{" "}
              <Link className="muted" href="/orders">Back to orders</Link>
            </p>
          )}
        </>
      )}
    </main>
  );
}
