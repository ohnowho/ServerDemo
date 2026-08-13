"use client";

import { useState } from "react";
import { API_BASE } from "@/lib/api";

const METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE"];
const BODY_METHODS = ["POST", "PUT", "PATCH"];

// 示例快捷入口：点击后自动填入表单，可随意增删
const QUICK_LINKS = [
  { method: "GET", path: "/api/users", body: "" },
  { method: "POST", path: "/api/users", body: '{\n  "username": "bob",\n  "email": "bob@example.com"\n}' },
  { method: "GET", path: "/api/users/1", body: "" },
];

interface ResponseState {
  status: number | null;
  statusText: string;
  body: string;
  error: string;
  timeMs: number;
}

export default function Page() {
  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("/api/users");
  const [body, setBody] = useState("");
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<ResponseState>({
    status: null,
    statusText: "",
    body: "",
    error: "",
    timeMs: 0,
  });

  async function send(m: string, p: string, b: string) {
    setLoading(true);
    const start = performance.now();
    try {
      const options: RequestInit = { method: m, headers: {} };
      if (BODY_METHODS.includes(m) && b.trim()) {
        options.headers = { "Content-Type": "application/json" };
        options.body = b;
      }
      const res = await fetch(`${API_BASE}${p}`, options);
      const text = await res.text();
      setResponse({
        status: res.status,
        statusText: res.statusText,
        body: formatBody(text),
        error: "",
        timeMs: Math.round(performance.now() - start),
      });
    } catch (err) {
      setResponse({
        status: null,
        statusText: "",
        body: "",
        error: `请求失败：${err instanceof Error ? err.message : err}（后端是否已启动？）`,
        timeMs: Math.round(performance.now() - start),
      });
    } finally {
      setLoading(false);
    }
  }

  function formatBody(text: string): string {
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text || "(空响应)";
    }
  }

  return (
    <main className="container">
      <h1>API 控制台</h1>
      <p className="subtitle">
        通用调试工具：选择方法、填写路径和 JSON，即可调用后端任何接口
      </p>

      <div className="card">
        <div className="row">
          <select value={method} onChange={(e) => setMethod(e.target.value)}>
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          <input
            className="path-input"
            value={path}
            onChange={(e) => setPath(e.target.value)}
            placeholder="/api/users"
            spellCheck={false}
          />
          <button onClick={() => send(method, path, body)} disabled={loading}>
            {loading ? "请求中..." : "发送"}
          </button>
        </div>

        {BODY_METHODS.includes(method) && (
          <textarea
            className="body-input"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder='{"key": "value"}'
            rows={4}
            spellCheck={false}
          />
        )}

        <div className="quick-links">
          <span className="label">快捷入口：</span>
          {QUICK_LINKS.map((q, i) => (
            <button
              key={i}
              className="chip"
              onClick={() => {
                setMethod(q.method);
                setPath(q.path);
                setBody(q.body);
                send(q.method, q.path, q.body);
              }}
            >
              {q.method} {q.path}
            </button>
          ))}
        </div>
      </div>

      <div className="card response-card">
        <div className="response-header">
          <span className="label">响应</span>
          {response.status !== null && (
            <span className={`status status-${statusClass(response.status)}`}>
              {response.status} {response.statusText}
              <span className="time">· {response.timeMs}ms</span>
            </span>
          )}
        </div>
        {response.error ? (
          <pre className="error">{response.error}</pre>
        ) : (
          <pre className="response-body">{response.body}</pre>
        )}
      </div>
    </main>
  );
}

function statusClass(code: number): string {
  if (code >= 200 && code < 300) return "ok";
  if (code >= 400 && code < 500) return "warn";
  if (code >= 500) return "err";
  return "ok";
}
