# 01 — Phase 0: Foundation & Alipay wiring

**Goal**: app compiles and boots with the Alipay SDK, config binding, shared error
handling, scheduling, and ID generation. No business logic yet.

## Files

| Op | Path |
|----|------|
| M | `backend/pom.xml` |
| M | `backend/src/main/resources/application.yml` |
| N | `backend/src/main/java/com/example/demo/config/AlipayProperties.java` |
| N | `backend/src/main/java/com/example/demo/config/AlipayConfig.java` |
| N | `backend/src/main/java/com/example/demo/config/SchedulingConfig.java` |
| N | `backend/src/main/java/com/example/demo/common/BusinessException.java` |
| N | `backend/src/main/java/com/example/demo/common/GlobalExceptionHandler.java` |
| N | `backend/src/main/java/com/example/demo/common/IdGenerator.java` |
| N | `backend/src/main/java/com/example/demo/common/Money.java` |

## Steps

### 1. Add SDK dependency (`pom.xml`)

```xml
<dependency>
    <groupId>com.alipay.sdk</groupId>
    <artifactId>alipay-sdk-java</artifactId>
    <version>4.40.272.ALL</version>
</dependency>
```

Verify with `./mvnw -q compile`. Expected transitive deps: gson, bouncycastle
(`bcprov-jdk15on`), commons-logging. Watch for `javax.servlet` classes — we never use
them (params are passed as `Map`), so no clash with Spring Boot 4 / jakarta.

### 2. Configuration (`application.yml`)

```yaml
alipay:
  sandbox: true
  app-id: ${ALIPAY_APP_ID:}
  private-key: ${ALIPAY_PRIVATE_KEY:}
  alipay-public-key: ${ALIPAY_PUBLIC_KEY:}
  notify-url: ${ALIPAY_NOTIFY_URL:http://localhost:8080/api/payments/alipay/notify}
  return-url: ${ALIPAY_RETURN_URL:http://localhost:3000/orders}
  order-timeout-minutes: 15
```

- Keys come from env vars; empty defaults keep local boot working.
- `AlipayProperties` (record + `@ConfigurationProperties("alipay")`): `appId,
  privateKey, alipayPublicKey, notifyUrl, returnUrl, sandbox, orderTimeoutMinutes`.
  Enable via `@ConfigurationPropertiesScan` on `DemoApplication` or `@EnableConfigurationProperties`.

### 3. `AlipayConfig`

```java
@Bean
AlipayClient alipayClient(AlipayProperties p) {
    String gateway = p.sandbox()
        ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
        : "https://openapi.alipay.com/gateway.do";
    return new DefaultAlipayClient(gateway, p.appId(), p.privateKey(),
            "json", "UTF-8", p.alipayPublicKey(), "RSA2");
}
```

### 4. `SchedulingConfig`

`@Configuration @EnableScheduling` — needed for the timeout job (phase 4).

### 5. Common classes

- `BusinessException extends RuntimeException`: fields `code`, `HttpStatus status`;
  constructor `(HttpStatus, String code, String message)`.
- `GlobalExceptionHandler` (`@RestControllerAdvice`):
  - `BusinessException` → status + `{code, message}`
  - `MethodArgumentNotValidException` → 400 `{code:"VALIDATION_ERROR", message, fields}`
  - `Exception` → 500 `{code:"INTERNAL_ERROR", message:"internal error"}` (log full stack)
- `IdGenerator`: `orderNo()` = `yyyyMMddHHmmss` + 8 random digits (e.g. `2026081310301512345678`);
  `paymentNo()` = `"PAY" + orderNo()`. Thread-safe (random per call).
- `Money`: `BigDecimal centsToYuan(long)` (divide 100, scale 2), `long yuanToCents(BigDecimal)`
  (multiply 100, `longValueExact()`).

## Acceptance criteria

- [ ] `./mvnw -q compile` passes with the SDK on the classpath.
- [ ] `./mvnw spring-boot:run` boots; hitting an unknown path returns
      `{"code":"INTERNAL_ERROR",...}` (or 404 fallback) JSON, not a whitelabel page.
- [ ] `curl -X POST http://localhost:8080/api/orders` (empty body) → 400 JSON error shape.
