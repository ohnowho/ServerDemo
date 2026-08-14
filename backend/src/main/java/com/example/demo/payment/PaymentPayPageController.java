package com.example.demo.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Money;
import com.example.demo.config.PaymentProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Renders the channel-specific pay page (server-side HTML). The buyer either gets
 * redirected to the channel (real Alipay form auto-submit, WeChat QR) or completes
 * the payment locally (simulation button / card form). Every page polls the payment
 * status and returns the user to the order list once the payment is terminal.
 *
 * Templating uses {@link String#replace} tokens (not printf-style formatting) so
 * channel payloads and CSS containing '%' are safe.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentPayPageController {

    private static final Gson GSON = new Gson();

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    public PaymentPayPageController(PaymentService paymentService, PaymentProperties paymentProperties) {
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
    }

    @GetMapping(value = "/{paymentNo}/pay", produces = MediaType.TEXT_HTML_VALUE)
    public String payPage(@PathVariable String paymentNo) {
        PaymentRecord record = paymentService.requireRecord(paymentNo);
        if (record.getType() != PaymentType.PAYMENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_PAYABLE", "refund records have no pay page");
        }
        String amount = Money.centsToYuan(record.getAmountCents()).toPlainString();
        String body = switch (record.getChannel()) {
            case CARD -> cardForm(amount, paymentNo);
            case ALIPAY -> record.isSimulated() ? simulateButton(amount, paymentNo) : alipayForm(amount, record.getPayload());
            case WECHAT -> record.isSimulated() ? simulateButton(amount, paymentNo) : wechatQr(amount, record.getPayload());
        };
        return shell(body, record.getOrderNo(), paymentNo);
    }

    private String shell(String body, String orderNo, String paymentNo) {
        String returnUrl = paymentProperties.returnUrl()
                + (paymentProperties.returnUrl().contains("?") ? "&" : "?")
                + "orderNo=" + orderNo + "&paid=1";
        return """
                <!doctype html>
                <html lang="zh">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Checkout</title>
                  <style>
                    body { font-family: -apple-system, "PingFang SC", sans-serif; background: #f1f5f9; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
                    .card { background: #fff; border-radius: 14px; padding: 32px; width: 360px; box-shadow: 0 10px 30px rgba(0,0,0,.08); text-align: center; }
                    .amount { font-size: 28px; font-weight: 700; margin-bottom: 16px; }
                    .btn { margin-top: 16px; width: 100%; padding: 12px; border: 0; border-radius: 8px; background: #2563eb; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; }
                    .hint { color: #64748b; font-size: 13px; margin-top: 12px; }
                    label { display: block; text-align: left; margin-top: 12px; font-size: 13px; color: #475569; }
                    input { width: 100%; margin-top: 4px; padding: 10px; border: 1px solid #cbd5e1; border-radius: 8px; box-sizing: border-box; }
                    img.qr { width: 220px; height: 220px; margin: 12px 0; }
                  </style>
                  <script>
                    var paymentNo = '{{PAYMENT_NO}}';
                    var returnUrl = '{{RETURN_URL}}';
                    async function poll() {
                      try {
                        var res = await fetch('/api/payments/' + paymentNo);
                        var data = await res.json();
                        if (data.status !== 'CREATED') { window.location.href = returnUrl; return; }
                      } catch (e) {}
                      setTimeout(poll, 1500);
                    }
                    setTimeout(poll, 1500);
                  </script>
                </head>
                <body>
                  <div class="card">
                    {{BODY}}
                  </div>
                </body>
                </html>
                """.replace("{{PAYMENT_NO}}", jsSafe(paymentNo))
                .replace("{{RETURN_URL}}", jsSafe(returnUrl))
                .replace("{{BODY}}", body);
    }

    private String simulateButton(String amount, String paymentNo) {
        return """
                <div class="amount">¥{{AMOUNT}}</div>
                <p class="hint">Simulation mode: complete this payment locally, no gateway involved.</p>
                <button id="pay" class="btn">Simulate successful payment</button>
                <script>
                  document.getElementById('pay').onclick = async function () {
                    var res = await fetch('/api/payments/{{PAYMENT_NO}}/simulate', { method: 'POST' });
                    if (res.ok) { window.location.href = returnUrl; } else { alert('Payment failed'); }
                  };
                </script>
                """.replace("{{AMOUNT}}", amount).replace("{{PAYMENT_NO}}", paymentNo);
    }

    private String cardForm(String amount, String paymentNo) {
        return """
                <div class="amount">¥{{AMOUNT}}</div>
                <form id="cardForm">
                  <label>Card number <input name="cardNumber" placeholder="4111 1111 1111 1111" required></label>
                  <label>Cardholder name <input name="name" placeholder="ZHANG SAN" required></label>
                  <label>Expiry (MM/YY) <input name="expiry" placeholder="08/29" pattern="[0-9]{2}/[0-9]{2}" required></label>
                  <label>CVV <input name="cvv" placeholder="123" pattern="[0-9]{3,4}" required></label>
                  <button class="btn" type="submit">Pay</button>
                </form>
                <p class="hint">Demo only: no real charge is made.</p>
                <script>
                  document.getElementById('cardForm').onsubmit = async function (e) {
                    e.preventDefault();
                    var f = e.target;
                    var payload = { cardNumber: f.cardNumber.value, name: f.name.value, expiry: f.expiry.value, cvv: f.cvv.value };
                    var res = await fetch('/api/payments/{{PAYMENT_NO}}/card', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(payload)
                    });
                    if (res.ok) { window.location.href = returnUrl; } else { alert('Payment failed'); }
                  };
                </script>
                """.replace("{{AMOUNT}}", amount).replace("{{PAYMENT_NO}}", paymentNo);
    }

    private String alipayForm(String amount, String payload) {
        return """
                <div class="amount">¥{{AMOUNT}}</div>
                <p class="hint">Redirecting to Alipay…</p>
                {{PAYLOAD}}
                <script>var form = document.querySelector('form'); if (form) form.submit();</script>
                """.replace("{{AMOUNT}}", amount).replace("{{PAYLOAD}}", payload);
    }

    private String wechatQr(String amount, String payload) {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);
        String codeUrl = json.has("codeUrl") ? json.get("codeUrl").getAsString() : "";
        String qr = URLEncoder.encode(codeUrl, StandardCharsets.UTF_8);
        return """
                <div class="amount">¥{{AMOUNT}}</div>
                <p class="hint">Scan the QR code with WeChat to complete the payment</p>
                <img class="qr" src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data={{QR}}" alt="WeChat Pay QR code">
                <p class="hint">The page will redirect automatically once paid</p>
                """.replace("{{AMOUNT}}", amount).replace("{{QR}}", qr);
    }

    private static String jsSafe(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
