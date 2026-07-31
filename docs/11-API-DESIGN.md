# 📖 API-DESIGN.md — REST API Contract ("Storybook" for Frontend)

Related: `04-SRS.md`, `09-UML-SEQUENCE-DIAGRAMS.md`, `openapi.yaml` (machine-readable version of this doc). This is the **single source of truth** the frontend team (M3/M4) builds against — treat any change here as a breaking-change discussion, not a silent edit.

> **Updated 31 Jul 2026** post-customer-meeting: added `/accounts` and `/analytics/*` endpoints, `sourceAccountId` replaces free-text `sourceAccount`, currency restricted to `INR`/`USD`, rate-limit headers/429 documented — MEM-017/018/019/020.

**Base URL (local dev):** `http://localhost:8080/api/v1`

---

## 1. Conventions

- All request/response bodies are `application/json`.
- All timestamps are ISO-8601 UTC, e.g. `2026-07-30T14:23:01Z`.
- `id` fields are UUID strings.
- **Supported currencies: `INR` and `USD` only** (customer-confirmed 31 Jul 2026, MEM-018). A payment's `currency` must equal its `sourceAccountId`'s currency.
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
  "destinationAccount": "ACC2000002",
  "reference": "Invoice #4471"
}
```

**Field rules:**
| Field | Type | Required | Rules |
|---|---|---|---|
| `sourceAccountId` | UUID | yes | Must reference an existing, `ACTIVE` `Account` (§3/§4) — unknown/inactive → `INVALID_ACCOUNT` |
| `amount` | number | yes | > 0, <= 1,000,000, max 2 decimal places |
| `currency` | string | yes | `INR` or `USD` only; **must equal the source account's currency** — mismatch → `INVALID_CURRENCY` |
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
- **`404 Not Found`** — `sourceAccountId` doesn't exist at all (`ACCOUNT_NOT_FOUND`) — distinguished from `INVALID_ACCOUNT` (400) which covers an account that exists but is `INACTIVE`, or a malformed `destinationAccount`.
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
    { "id": "...", "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111", "amount": 250.00, "currency": "INR", "status": "FAILED", "errorCode": "NETWORK_ERROR", "createdAt": "..." }
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
| INVALID_CURRENCY | 400 | Currency not `INR`/`USD`, or ≠ source account's currency |
| INVALID_AMOUNT | 400 | Amount <= 0, > max, or wrong decimal precision |
| DUPLICATE_PAYMENT | *(reserved, not used by default — see MEM-006)* | Would apply only if strict-reject mode chosen instead of 200+existing |
| INVALID_STATUS_TRANSITION | 400 | Illegal transition attempted |
| PAYMENT_NOT_FOUND | 404 | Unknown payment ID |
| PROCESSING_ERROR | 500 (or 409 for lock conflicts) | Internal/simulated processing error, optimistic lock conflict |
| NETWORK_ERROR | 503 *(reflected in payment body as-is; HTTP response for the *triggering* explicit endpoint call itself is 200 since the transition to FAILED succeeded as an operation)* | Simulated network failure |
| INSUFFICIENT_FUNDS | 400 | Reserved — not actively triggered in core scope (no real balance ledger), available for a stretch enhancement |
| **RATE_LIMIT_EXCEEDED** *(new)* | **429** | Token bucket exhausted (global or per-client) — MEM-020 |

> **Important distinction:** For **explicit transition endpoints**, a "successful transition to FAILED" is still an HTTP `200` (the API call itself succeeded — it did what was asked: attempt the transition, and correctly recorded a failure). HTTP error statuses (`400/404/409/429/500/503`) are reserved for when the **API call itself** couldn't be honored (bad request shape, illegal transition, not found, conflict, rate-limited, internal fault) — not for "the payment's business outcome was a failure." This distinction is called out explicitly to avoid frontend confusion.

## 13. Frontend Integration Notes

- **Landing page is the KPI dashboard** (`GET /analytics/summary` + `/analytics/trend`), per customer directive — not the Create Payment form.
- On the Create Payment screen, call `GET /accounts` first to populate the **source-account dropdown**; currency field auto-populates/locks to the selected account's currency (client-side convenience; server still validates independently).
- Use `GET /payments/{id}` (short poll every 1–2s, or just once since auto-progression is synchronous in Sprint 1) to reflect final status after `POST /payments`.
- Status badge color mapping (Appendix D): 🟢 `COMPLETED`, 🟡 `CREATED`/`VALIDATED`/`SENT`, 🔴 `FAILED`.
- **Handle `429` gracefully:** show a "please slow down" toast using the `Retry-After` header rather than a generic error (new, MEM-020).
- **Idempotency-Key generation rule (see `04-SRS.md` §7a for full scenario table):**
  1. Generate the key (`crypto.randomUUID()`) **once**, at the moment the Submit button is clicked — store it in component state/ref tied to that submission attempt.
  2. **Disable the Submit button immediately** on click (before the request even fires) — this is the primary defense against double-click generating two different keys for what the user intended as one action.
  3. If the request fails with a **retryable** error (network/timeout) and you show a "Retry" action, **reuse the same stored key** for the retry.
  4. On success, or on a **non-retryable** failure the user must fix (e.g. validation error — they'll edit the form and resubmit), **discard the key** — re-enable the form for a fresh submission, which will generate a **new** key next time.
  5. Do **NOT** compute the key from form field values (no hashing amount+accounts) — that would incorrectly block a user's legitimate, separate repeat payment. See worked scenario #4 in `04-SRS.md` §7a.


