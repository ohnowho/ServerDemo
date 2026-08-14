import { Suspense } from "react";
import OrdersClient from "./orders-client";

export default function OrdersPage() {
  return (
    <Suspense
      fallback={
        <main className="container">
          <h1>Orders</h1>
          <p className="subtitle">Loading…</p>
        </main>
      }
    >
      <OrdersClient />
    </Suspense>
  );
}
