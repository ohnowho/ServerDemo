import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Shop Demo",
  description: "Order, stock and Alipay payment demo",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <nav className="topnav">
          <span className="brand">ShopDemo</span>
          <div className="links">
            <Link href="/">Shop</Link>
            <Link href="/orders">Orders</Link>
            <Link href="/console">API Console</Link>
          </div>
        </nav>
        {children}
      </body>
    </html>
  );
}
