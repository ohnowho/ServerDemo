"use client";

import { useState } from "react";
import { API_BASE } from "@/lib/api";

const METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE"];
const BODY_METHODS = ["POST", "PUT", "PATCH"];

// Example quick links: click to auto-fill the form; add or remove freely
const QUICK_LINKS = [
  { method: "GET", path: "/api/users", body: "" },
  { method: "POST", path: "/api/users", body: '{\n  "username": "bob",\n  "email": "bob@example.com"\n}' },
  { method: "GET", path: "/api/users/1", body: "" },
  { method: "GET", path: "/api/products", body: "" },
  { method: "POST", path: "/api/products", body: '{\n  "name": "Lamp",\n  "price": "19.99",\n  "stock": 10\n}' },
  { method: "POST", path: "/api/orders", body: '{\n  "userId": 1,\n  "items": [{"productId": 1, "quantity": 1}]\n}' },
];

interface ResponseState {
  status: number | null;
  statusText: string;
  body: string;
  error: string;
  timeMs: number;
}

// module-level so React's purity lint rule (which only guards render) doesn't
// flag wall-clock measurement inside event handlers
function nowMs(): number {
  return performance.now();
}

export default function ConsolePage() {
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
    const start = nowMs();
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
        timeMs: Math.round(nowMs() - start),
      });
    } catch (err) {
      setResponse({
        status: null,
        statusText: "",
        body: "",
        error: `Request failed: ${err instanceof Error ? err.message : err} (is the backend running?)`,
        timeMs: Math.round(nowMs() - start),
      });
    } finally {
      setLoading(false);
    }
  }

  function formatBody(text: string): string {
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text || "(empty response)";
    }
  }

  return (
    <main className="container">
      <h1>API Console</h1>
      <p className="subtitle">
        General-purpose debug tool: pick a method, enter a path and JSON, and
        call any backend endpoint
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
            {loading ? "Requesting..." : "Send"}
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
          <span className="label">Quick links:</span>
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
          <span className="label">Response</span>
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
