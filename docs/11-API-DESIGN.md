# 📖 API-DESIGN.md — REST API Contract ("Storybook" for Frontend)

Related: `04-SRS.md`, `09-UML-SEQUENCE-DIAGRAMS.md`, `openapi.yaml` (machine-readable version of this doc). This is the **single source of truth** the frontend team (M3/M4) builds against — treat any change here as a breaking-change discussion, not a silent edit.

> **Updated 31 Jul 2026** post-customer-meeting: added `/accounts` and `/analytics/*` endpoints, `sourceAccountId` replaces free-text `sourceAccount`, currency restricted to `INR`/`USD`, rate-limit headers/429 documented — MEM-017/018/019/020.

**Base URL (local dev):** `http://localhost:8080/api/v1`

---

## 1. Conventions

- All request/response bodies are `application/json`.
- All timestamps are ISO-8601 UTC, e.g. `2026-07-30T14:23:01Z`.
- `id` fields are UUID strings.
- **Supported currencies: `INR` and `USD` only** (customer-confirmed 31 Jul 2026, MEM-018). A payment's source/debit `currency` must equal its `sourceAccountId`'s currency. V2 feature #20 adds an optional cross-currency payout via `targetCurrency`, using a **hardcoded current INR↔USD rate** (not a live lookup).
- Idempotency key is passed as a request **header**: `Idempotency-Key: <client-generated-string>` (preferred over body field — standard REST convention, keeps it out of the domain payload). *(Supersedes earlier SRS draft that showed it as a body field — see MEM-013.)* **Important:** this key identifies a single *submission attempt*, not the payment's business content — it must be generated fresh (e.g. `crypto.randomUUID()`) client-side once per attempt, never derived from `amount`/`sourceAccountId` fields. Full rationale + worked scenarios: `04-SRS.md` §7a / FR-1.1a.
- Pagination: query params `page` (0-based, default `0`), `size` (default `20`, max `100`), response wrapped in a `Page` envelope.
- **Rate limiting (new, MEM-020):** every response includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers. Exceeding the limit returns `429 Too Many Requests` with a `Retry-After` header (seconds) and the standard error body below, `errorCode: RATE_LIMIT_EXCEEDED`.
- All error responses share the shape:
```json
{
  "errorCode": "INVALID_AMOUNT",
  "message": "Amount must be greater than 0",
  "timestamp": "2026-07-30T14:23:01Z",
  "path": "/api/v1/payments"
}
```
- **Security note (new, MEM-022):** this shape is the *only* thing ever returned on error — no stack traces, no internal exception class names. All endpoints require HTTPS in deployed environments; CORS is restricted to the known frontend origin.

## 2. Endpoint Summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/accounts` | List the operator's accounts (source-account picker) *(new)* |
| GET | `/accounts/{id}` | Get a single account *(new)* |
| POST | `/payments` | Create a new payment |
| GET | `/payments/{id}` | Get a payment by ID |
| GET | `/payments` | List/search/filter payments (paginated) |
| GET | `/payments/{id}/history` | Get full status-transition audit trail |
| POST | `/payments/{id}/validate` | Explicitly trigger `CREATED→VALIDATED` transition |
| POST | `/payments/{id}/send` | Explicitly trigger `VALIDATED→SENT` transition |
| POST | `/payments/{id}/complete` | Explicitly trigger `SENT→COMPLETED` transition |
| GET | `/analytics/summary` | KPI dashboard summary (success rate, failure rate, avg processing time, throughput, volume) *(new)* |
| GET | `/analytics/trend` | Time-bucketed trend (payments per hour, last 24h, by status) *(new)* |
| POST | `/payments/{id}/cancel` | **(V2)** Cancel a `CREATED` payment — feature #18 |
| POST | `/payments/{id}/reverse` | **(V2)** Reverse a `COMPLETED` payment (creates a new, linked payment) — feature #19 |
| GET | `/payments/export` | **(V2)** Stream the current filtered payment list as CSV — feature #14 |
| GET | `/analytics/stream` | **(V2)** Server-Sent Events stream of live KPI updates — replaces the 30s dashboard poll |
| GET | `/fx/rate` | **(V2)** Hardcoded FX conversion rate used for feature #20 |
| POST | `/notifications/send` | **(V2, was already implemented, now documented)** Send an ad-hoc email notification |
| GET | `/notifications` | **(V2, now documented)** List notification audit logs |
| GET | `/notifications/{id}` | **(V2, now documented)** Get a single notification log |
| GET | `/notifications/by-payment/{paymentId}` | **(V2, now documented)** List notifications for a payment |

> Note (MEM-007): payments **auto-progress** through the full lifecycle immediately on creation for demo speed. The explicit `/validate`, `/send`, `/complete` endpoints exist for testing, manual control, and demonstrating rejection of illegal transitions — the frontend's primary flow only needs `GET /accounts` + `POST /payments` + polling/`GET`, not manual calls to these.

---

## 3. `GET /accounts` — List the Operator's Accounts (new, MEM-017)

**Response `200 OK`:**
```json
[
  { "id": "b2c3d4e5-1111-4a11-8a11-111111111111", "label": "Primary INR Savings", "accountNumber": "ACC1000001", "currency": "INR", "status": "ACTIVE" },
  { "id": "c3d4e5f6-2222-4a22-8a22-222222222222", "label": "USD Wallet", "accountNumber": "ACC2000002", "currency": "USD", "status": "ACTIVE" }
]
```
Used by the frontend to populate the **source-account dropdown** on the Create Payment screen (per customer directive: "user will see during payment option at UI and select one of them"). This endpoint is also rate-limited (`429`) like every other endpoint — see §1.

## 4. `GET /accounts/{id}` — Get a Single Account (new)

- **`200 OK`** — same shape as one array element above.
- **`404 Not Found`** — `ACCOUNT_NOT_FOUND`.
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

---

## 5. `POST /payments` — Create Payment

**Headers:** `Idempotency-Key: <string>` *(optional but recommended)*

**Request Body:**
```json
{
  "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111",
  "amount": 250.00,
  "currency": "INR",
  "targetCurrency": "USD",
  "destinationAccount": "ACC2000002",
  "reference": "Invoice #4471"
}
```

**Field rules:**
| Field | Type | Required | Rules |
|---|---|---|---|
| `sourceAccountId` | UUID | yes | Must reference an existing, `ACTIVE` `Account` (§3/§4) — unknown → `404 ACCOUNT_NOT_FOUND`, inactive → `400 INVALID_ACCOUNT` |
| `amount` | number | yes | > 0, <= 1,000,000, max 2 decimal places |
| `currency` | string | yes | Source/debit currency. `INR` or `USD` only; **must equal the source account's currency** — mismatch → `INVALID_CURRENCY` |
| `targetCurrency` | string | yes | Payout currency. `INR` or `USD` only; may equal or differ from `currency`. If different, backend converts using the current hardcoded INR↔USD rate. |
| `destinationAccount` | string | yes | 8–20 alphanumeric chars, must differ from the source account's `accountNumber` (format-only — external party, not existence-checked, MEM-017) |
| `reference` | string | no | max 255 chars |

**Responses:**

- **`201 Created`** — new payment created (regardless of whether it ends up `COMPLETED` or `FAILED` after auto-progression — the *resource* was successfully created; the body reflects final status).
  - Header: `Location: /api/v1/payments/{id}`
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111",
  "amount": 250.00,
  "currency": "INR",
  "targetCurrency": "USD",
  "convertedAmount": 3.00,
  "fxRate": 0.012,
  "destinationAccount": "ACC2000002",
  "reference": "Invoice #4471",
  "status": "COMPLETED",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-07-30T14:23:01Z",
  "updatedAt": "2026-07-30T14:23:01Z"
}
```
- **`200 OK`** — `Idempotency-Key` matched an existing payment; body = the existing payment (MEM-006), no new resource created.
- **`400 Bad Request`** — field-level or business-rule validation failure (`VALIDATION_FAILED`, `INVALID_AMOUNT`, `INVALID_CURRENCY`, `INVALID_ACCOUNT`).
- **`404 Not Found`** — `sourceAccountId` doesn't exist at all (`ACCOUNT_NOT_FOUND`), or the requested FX pair has no configured hardcoded rate (`FX_RATE_UNAVAILABLE`). Distinguished from `INVALID_ACCOUNT` (400) which covers an account that exists but is `INACTIVE`, or a malformed `destinationAccount`.
- **`429 Too Many Requests`** — rate limit exceeded (`RATE_LIMIT_EXCEEDED`, new).

---

## 6. `GET /payments/{id}` — Get Payment by ID

**Responses:**
- **`200 OK`**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111",
  "amount": 250.00,
  "currency": "INR",
  "targetCurrency": "USD",
  "convertedAmount": 3.00,
  "fxRate": 0.012,
  "destinationAccount": "ACC2000002",
  "reference": "Invoice #4471",
  "status": "FAILED",
  "errorCode": "NETWORK_ERROR",
  "errorMessage": "Simulated network failure while transmitting payment",
  "createdAt": "2026-07-30T14:23:01Z",
  "updatedAt": "2026-07-30T14:23:02Z"
}
```
- **`404 Not Found`**
```json
{ "errorCode": "PAYMENT_NOT_FOUND", "message": "No payment found with id a1b2...", "timestamp": "...", "path": "/api/v1/payments/a1b2..." }
```
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

---

## 7. `GET /payments` — List / Search / Filter

**Query params:**
| Param | Type | Notes |
|---|---|---|
| `status` | string | optional, one of `CREATED, VALIDATED, SENT, COMPLETED, FAILED` |
| `search` | string | optional, matches against `reference` or `id` (partial match) |
| `sourceAccountId` | UUID | optional, filter to payments from a specific account *(new)* |
| `page` | int | default 0 |
| `size` | int | default 20, max 100 |
| `sort` | string | default `createdAt,desc` |

**Example:** `GET /payments?status=FAILED&page=0&size=20`

**Response `200 OK`:**
```json
{
  "content": [
    { "id": "...", "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111", "amount": 250.00, "currency": "INR", "targetCurrency": "USD", "convertedAmount": 3.00, "fxRate": 0.012, "status": "FAILED", "errorCode": "NETWORK_ERROR", "createdAt": "..." }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

---

## 8. `GET /payments/{id}/history` — Status History / Audit Trail

**Response `200 OK`:**
```json
[
  { "previousStatus": null, "newStatus": "CREATED", "errorCode": null, "errorMessage": null, "triggeredBy": "CLIENT", "occurredAt": "2026-07-30T14:23:01Z" },
  { "previousStatus": "CREATED", "newStatus": "VALIDATED", "errorCode": null, "errorMessage": null, "triggeredBy": "SYSTEM", "occurredAt": "2026-07-30T14:23:01Z" },
  { "previousStatus": "VALIDATED", "newStatus": "FAILED", "errorCode": "NETWORK_ERROR", "errorMessage": "Simulated network failure", "triggeredBy": "SYSTEM", "occurredAt": "2026-07-30T14:23:02Z" }
]
```
- **`404 Not Found`** if the payment ID doesn't exist (`PAYMENT_NOT_FOUND`).
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

---

## 9. `POST /payments/{id}/validate` | `/send` | `/complete` — Explicit Transitions

No request body. Behave exactly like the corresponding automatic step (§5), but callable independently — primarily for testing/demo of the state machine's guard logic.

**Responses:**
- **`200 OK`** — updated `PaymentResponse` (same shape as §6).
- **`400 Bad Request`** — illegal transition attempted:
```json
{ "errorCode": "INVALID_STATUS_TRANSITION", "message": "Cannot transition from COMPLETED to VALIDATED", "timestamp": "...", "path": "/api/v1/payments/.../validate" }
```
- **`404 Not Found`** — unknown payment ID.
- **`409 Conflict`** *(concurrency, NFR-8)* — optimistic lock conflict if two requests race:
```json
{ "errorCode": "PROCESSING_ERROR", "message": "Payment was concurrently modified, please retry", "timestamp": "...", "path": "..." }
```
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

---

## 10. `GET /analytics/summary` — KPI Dashboard Summary (new, MEM-019)

**Query params:** `from`, `to` (ISO-8601, optional — default: last 24h).

**Response `200 OK`:**
```json
{
  "totalPayments": 1287,
  "successRatePct": 94.2,
  "failureRatePct": 5.8,
  "avgProcessingTimeSeconds": 0.84,
  "throughputPerMinute": 53.6,
  "volumeByCurrency": { "INR": 1845200.00, "USD": 42310.50 },
  "topFailureReasons": { "NETWORK_ERROR": 41, "INVALID_CURRENCY": 12, "PROCESSING_ERROR": 9 }
}
```
> Deliberately **no** vanity metrics (no "total users", no gamified badges) — every field answers a real payments-ops question, per explicit customer guidance (MEM-019).

## 11. `GET /analytics/trend` — Basic Trend Chart Data (new, MEM-019)

**Query params:** `hours` (default 24).

**Response `200 OK`:**
```json
{
  "buckets": [
    { "periodStart": "2026-07-31T09:00:00Z", "created": 40, "completed": 35, "failed": 5 },
    { "periodStart": "2026-07-31T10:00:00Z", "created": 52, "completed": 48, "failed": 4 }
  ]
}
```

---

## 12. Error Code → HTTP Status Reference (adopted from brief Appendix B, extended post-customer-meeting)

| Error Code | HTTP Status | Triggered by |
|---|---|---|
| VALIDATION_FAILED | 400 | Generic field-level validation |
| INVALID_ACCOUNT | 400 | Unknown/inactive `sourceAccountId`, bad `destinationAccount` format, or source==destination |
| ACCOUNT_NOT_FOUND | 404 | `GET /accounts/{id}` with unknown ID *(new)* |
| INVALID_CURRENCY | 400 | Source or target currency not `INR`/`USD`, or source/debit `currency` ≠ source account's currency |
| INVALID_AMOUNT | 400 | Amount <= 0, > max, or wrong decimal precision |
| DUPLICATE_PAYMENT | *(reserved, not used by default — see MEM-006)* | Would apply only if strict-reject mode chosen instead of 200+existing |
| INVALID_STATUS_TRANSITION | 400 | Illegal transition attempted |
| PAYMENT_NOT_FOUND | 404 | Unknown payment ID |
| PROCESSING_ERROR | 500 (or 409 for lock conflicts) | Internal/simulated processing error, optimistic lock conflict |
| NETWORK_ERROR | 503 *(reflected in payment body as-is; HTTP response for the *triggering* explicit endpoint call itself is 200 since the transition to FAILED succeeded as an operation)* | Simulated network failure |
| INSUFFICIENT_FUNDS | 400 | Reserved — not actively triggered in core scope (no real balance ledger), available for a stretch enhancement |
| RATE_LIMIT_EXCEEDED | 429 | Token bucket exhausted (global or per-client) — MEM-020 |
| **PAYMENT_NOT_CANCELLABLE** *(new, V2)* | **409** | `POST /payments/{id}/cancel` attempted on a payment not in `CREATED` status — MEM-029 |
| **PAYMENT_ALREADY_REVERSED** *(new, V2)* | **409** | `POST /payments/{id}/reverse` attempted on a payment already flagged `reversed=true` — MEM-030 |
| **EXPORT_TOO_LARGE** *(new, V2)* | **400** | `GET /payments/export` filter would exceed `paypulse.export.max-rows` — MEM-032 |
| **FX_RATE_UNAVAILABLE** *(new, V2)* | **404** | `GET /fx/rate` or `POST /payments` requested a currency pair with no configured hardcoded rate — MEM-031 |

> **Important distinction:** For **explicit transition endpoints**, a "successful transition to FAILED" is still an HTTP `200` (the API call itself succeeded — it did what was asked: attempt the transition, and correctly recorded a failure). HTTP error statuses (`400/404/409/429/500/503`) are reserved for when the **API call itself** couldn't be honored (bad request shape, illegal transition, not found, conflict, rate-limited, internal fault) — not for "the payment's business outcome was a failure." This distinction is called out explicitly to avoid frontend confusion.

## 13. Frontend Integration Notes

- **Landing page is the KPI dashboard** (`GET /analytics/summary` + `/analytics/trend`), per customer directive — not the Create Payment form.
- On the Create Payment screen, call `GET /accounts` first to populate the **source-account dropdown**; the source/debit `currency` auto-populates/locks to the selected account's currency, while `targetCurrency` may be chosen separately for cross-currency payouts. Use `GET /fx/rate` to preview the hardcoded conversion before submit; the backend recomputes the official `convertedAmount`/`fxRate` on create.
- Use `GET /payments/{id}` (short poll every 1–2s, or just once since auto-progression is synchronous in Sprint 1) to reflect final status after `POST /payments`.
- Status badge color mapping (Appendix D): 🟢 `COMPLETED`, 🟡 `CREATED`/`VALIDATED`/`SENT`, 🔴 `FAILED`, ⚪ `CANCELLED` *(new, V2)*.
- **Handle `429` gracefully:** show a "please slow down" toast using the `Retry-After` header rather than a generic error (new, MEM-020).
- **Idempotency-Key generation rule (see `04-SRS.md` §7a for full scenario table):**
  1. Generate the key (`crypto.randomUUID()`) **once**, at the moment the Submit button is clicked — store it in component state/ref tied to that submission attempt.
  2. **Disable the Submit button immediately** on click (before the request even fires) — this is the primary defense against double-click generating two different keys for what the user intended as one action.
  3. If the request fails with a **retryable** error (network/timeout) and you show a "Retry" action, **reuse the same stored key** for the retry.
  4. On success, or on a **non-retryable** failure the user must fix (e.g. validation error — they'll edit the form and resubmit), **discard the key** — re-enable the form for a fresh submission, which will generate a **new** key next time.
  5. Do **NOT** compute the key from form field values (no hashing amount+accounts) — that would incorrectly block a user's legitimate, separate repeat payment. See worked scenario #4 in `04-SRS.md` §7a.

---

## V2 API Additions (05 Aug 2026 — MEM-023–034)

## 14. `POST /payments/{id}/cancel` — Cancel a Payment (new, feature #18, MEM-029)

No request body. Legal **only** while the payment is `CREATED`.

**Responses:**
- **`200 OK`** — payment now `CANCELLED` (same `PaymentResponse` shape as §6, `status: "CANCELLED"`).
- **`409 Conflict`** — payment is not `CREATED`:
```json
{ "errorCode": "PAYMENT_NOT_CANCELLABLE", "message": "Payment ... is in status VALIDATED and can no longer be cancelled", "timestamp": "...", "path": "/api/v1/payments/.../cancel" }
```
- **`404 Not Found`** — unknown payment ID (`PAYMENT_NOT_FOUND`).
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

## 15. `POST /payments/{id}/reverse` — Reverse a Completed Payment (new, feature #19, MEM-030)

No request body. Legal **only** for a `COMPLETED` payment that has not already been reversed.

**Responses:**
- **`201 Created`** — a **new**, independent payment is created (offsetting the original — source/destination swapped, same amount/currency). Response body is the **new** payment's `PaymentResponse`, with an additional field `reversalOfPaymentId` pointing back to the original.
  - Header: `Location: /api/v1/payments/{newId}`
- **`409 Conflict`** — already reversed:
```json
{ "errorCode": "PAYMENT_ALREADY_REVERSED", "message": "Payment ... has already been reversed (see reversalPaymentId)", "timestamp": "...", "path": "..." }
```
- **`409 Conflict`** — original is not `COMPLETED`: `errorCode: INVALID_STATUS_TRANSITION`.
- **`404 Not Found`** — unknown payment ID (`PAYMENT_NOT_FOUND`).
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

> **`PaymentResponse` gains two new optional fields (V2, backward-compatible additions):** `reversed` (boolean, default `false`) and `reversalPaymentId`/`reversalOfPaymentId` (nullable UUID) — see updated schema in `openapi.yaml`.

## 16. `GET /payments/export` — CSV Export (new, feature #14, MEM-032)

**Query params:** identical to `GET /payments` (§7) — `status`, `search`, `sourceAccountId`, `sort` — **no `page`/`size`** (the full filtered set is exported, up to the row cap).

**Responses:**
- **`200 OK`** — `Content-Type: text/csv`, `Content-Disposition: attachment; filename="payments-export-<timestamp>.csv"`. Body streamed, columns: `id,sourceAccountId,destinationAccount,amount,currency,status,errorCode,createdAt,updatedAt`.
- **`400 Bad Request`** — filtered result exceeds the configured cap:
```json
{ "errorCode": "EXPORT_TOO_LARGE", "message": "Filtered result (127,412 rows) exceeds the export limit of 50,000 — please narrow your filters", "timestamp": "...", "path": "/api/v1/payments/export" }
```
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

## 17. `GET /analytics/stream` — Live Dashboard Updates via SSE (new, MEM-027)

`Content-Type: text/event-stream`. Client connects via `new EventSource('/api/v1/analytics/stream')`. Server pushes a `KpiSummaryResponse` (§10 shape) as a `data:` event whenever a payment status transition occurs, debounced to at most 1 push per 2 seconds. Connection stays open indefinitely (subject to the reverse proxy/nginx timeout config — see `frontend/nginx/nginx.conf`, which must disable proxy buffering for this route). No polling needed on the client once connected; frontend falls back to the previous 30s poll if the connection cannot be established or drops repeatedly.

**Responses:**
- **`200 OK`** — long-lived `text/event-stream` connection.
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED` (rejected before the stream opens, same as any other endpoint).

## 18. `GET /fx/rate` — Hardcoded FX Conversion Rate (new, feature #20, MEM-031)

**Query params:** `from` (`INR`/`USD`), `to` (`INR`/`USD`).

**Response `200 OK`:**
```json
{ "from": "INR", "to": "USD", "rate": 0.012, "asOf": "2026-08-05T00:00:00Z" }
```
- **`404 Not Found`** — unsupported/unconfigured pair: `errorCode: FX_RATE_UNAVAILABLE`.
- **`429 Too Many Requests`** — `RATE_LIMIT_EXCEEDED`.

> **This rate is hardcoded/configured, not live.** It is used both for frontend preview and for backend conversion when `targetCurrency` differs from source `currency`, so the preview and persisted `convertedAmount` stay aligned.

## 19. Notification Endpoints (already implemented backend-side; formally documented here, V2)

### `POST /notifications/send` — Send an Ad-Hoc Notification
**Request Body:**
```json
{
  "recipientEmail": "user@example.com",
  "recipientName": "John Doe",
  "event": "PAYMENT_COMPLETED",
  "paymentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "variables": { "amount": "1500.00", "currency": "INR", "referenceId": "PAY-REF-001" }
}
```
- **`200 OK`** — `EmailResult { success: true, ... }`.
- **`500 Internal Server Error`** — `EmailResult { success: false, ... }` (send failed — note: this endpoint's own error shape is `EmailResult`, not the standard `ApiError`, since it reflects the *email provider's* outcome, not an API request-validation failure).

### `GET /notifications` — List Notification Audit Logs
Query params: `status` (optional, `PENDING`/`SENT`/`FAILED`), `page`, `size`. Returns a paginated list of `NotificationLog` entries (see `V5__create_notification_log_table.sql` for the full shape).

### `GET /notifications/{notificationId}` — Get a Single Notification Log
`200 OK` + `NotificationLog`, or `404 Not Found` if unknown.

### `GET /notifications/by-payment/{paymentId}` — All Notifications for a Payment
`200 OK` + array of `NotificationLog` (empty array if none sent yet — e.g. no `owner_email` configured, MEM-026).

## 20. Sortable Columns — Formal Sort Field Allow-List (new, feature #16, MEM-033)

`GET /payments` and `GET /payments/export`'s `sort` parameter is restricted to: `createdAt`, `amount`, `status` (each combinable with `,asc` or `,desc`). Any other field value returns `400 VALIDATION_FAILED`. This was already an accepted parameter in Core (default `createdAt,desc`) — V2 simply formalizes the allow-list and wires the frontend's clickable column headers to it.

## 21. Copy Payment ID / Deep Linking — Stable URL Contracts (new, feature #17, MEM-034)

The following frontend URL patterns are formally committed as **stable, supported deep links** (no new backend endpoints):
- `payment-details.html?id=<uuid>` — opens directly to a payment's detail view.
- `payments.html?status=<CREATED|VALIDATED|SENT|COMPLETED|FAILED|CANCELLED>` — opens the payment list pre-filtered to that status.

Copy-to-clipboard is a pure frontend affordance (no API involvement) using `navigator.clipboard.writeText(paymentId)`.

