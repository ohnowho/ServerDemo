import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "API 控制台",
  description: "通用 API 调试控制台",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
