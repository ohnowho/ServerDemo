// Base URL of the backend API - change only this value
export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

// ---------- Types matching the backend ----------

export interface Product {
  id: number;
  name: string;
  price: string;
  stock: number;
  status: "ON_SALE" | "OFF_SALE";
}

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "CLOSED" | "REFUNDED";

export interface OrderItem {
  productId: number;
  productName: string;
  price: string;
  quantity: number;
  subtotal: string;
}

export interface Order {
  orderNo: string;
  userId: number;
  status: OrderStatus;
  totalAmount: string;
  expiresAt: string;
  createdAt: string;
  paidAt: string | null;
  closedAt: string | null;
  paymentNo: string | null;
  items: OrderItem[];
}

export type PaymentChannel = "ALIPAY" | "WECHAT" | "CARD";

export interface PaymentCreateResult {
  paymentNo: string;
  payUrl: string; // relative to the backend; prefix with API_BASE to navigate
}

export type PaymentStatus = "CREATED" | "SUCCESS" | "FAILED" | "CLOSED" | "REFUNDED";

export interface PaymentStatusResponse {
  paymentNo: string;
  orderNo: string;
  channel: PaymentChannel;
  type: "PAYMENT" | "REFUND";
  status: PaymentStatus;
  channelTradeNo: string | null;
  amount: string;
}

// ---------- HTTP helpers ----------

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const options: RequestInit = { method, headers: {} };
  if (body !== undefined) {
    options.headers = { "Content-Type": "application/json" };
    options.body = JSON.stringify(body);
  }
  const res = await fetch(`${API_BASE}${path}`, options);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const message = data?.message ?? `${res.status} ${res.statusText}`;
    throw new Error(message);
  }
  return data as T;
}

export function getJson<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export function postJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}
