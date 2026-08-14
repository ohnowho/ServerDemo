"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getJson, postJson, type Order, type Product } from "@/lib/api";

const DEMO_USER_ID = 1;

export default function Page() {
  const router = useRouter();
  const [products, setProducts] = useState<Product[]>([]);
  const [quantity, setQuantity] = useState<Record<number, number>>({});
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getJson<Product[]>("/api/products")
      .then(setProducts)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  async function buy(product: Product) {
    const qty = quantity[product.id] ?? 1;
    setBusyId(product.id);
    setError(null);
    try {
      const order = await postJson<Order>("/api/orders", {
        userId: DEMO_USER_ID,
        items: [{ productId: product.id, quantity: qty }],
      });
      router.push(`/pay/${order.orderNo}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusyId(null);
    }
  }

  if (error && products.length === 0) {
    return (
      <main className="container">
        <h1>Shop</h1>
        <p className="subtitle">Demo storefront (user {DEMO_USER_ID})</p>
        <p className="error-text">Could not load products: {error}</p>
      </main>
    );
  }

  return (
    <main className="container">
      <h1>Shop</h1>
      <p className="subtitle">
        Demo storefront (user {DEMO_USER_ID}) · payments via Alipay / WeChat Pay / credit card
      </p>
      {error && <p className="error-text">{error}</p>}
      <div className="grid">
        {products.map((product) => {
          const qty = quantity[product.id] ?? 1;
          const disabled = product.stock === 0 || busyId !== null;
          return (
            <div className="card product-card" key={product.id}>
              <h2>{product.name}</h2>
              <p className="price">¥{product.price}</p>
              <p className="stock">Stock: {product.stock}</p>
              <div className="row">
                <button
                  className="icon-btn"
                  disabled={busyId !== null || qty <= 1}
                  onClick={() =>
                    setQuantity((q) => ({ ...q, [product.id]: Math.max(1, qty - 1) }))
                  }
                >
                  −
                </button>
                <span className="qty">{qty}</span>
                <button
                  className="icon-btn"
                  disabled={busyId !== null || qty >= product.stock}
                  onClick={() =>
                    setQuantity((q) => ({ ...q, [product.id]: Math.min(product.stock, qty + 1) }))
                  }
                >
                  +
                </button>
                <button
                  className="buy-btn"
                  disabled={disabled}
                  onClick={() => buy(product)}
                >
                  {busyId === product.id ? "Buying…" : "Buy"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
      {products.length === 0 && !error && (
        <p className="subtitle">No products yet — create one via the API Console.</p>
      )}
    </main>
  );
}
