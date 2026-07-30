# 📖 API-DESIGN.md — REST API Contract ("Storybook" for Frontend)

Related: `04-SRS.md`, `09-UML-SEQUENCE-DIAGRAMS.md`, `openapi.yaml` (machine-readable version of this doc). This is the **single source of truth** the frontend team (M3/M4) builds against — treat any change here as a breaking-change discussion, not a silent edit.

**Base URL (local dev):** `http://localhost:8080/api/v1`

---

## 1. Conventions

- All request/response bodies are `application/json`.
- All timestamps are ISO-8601 UTC, e.g. `2026-07-30T14:23:01Z`.
- `id` fields are UUID strings.
- Idempotency key is passed as a request **header**: `Idempotency-Key: <client-generated-string>` (preferred over body field — standard REST convention, keeps it out of the domain payload). *(Supersedes earlier SRS draft that showed it as a body field — see MEM-013.)* **Important:** this key identifies a single *submission attempt*, not the payment's business content — it must be generated fresh (e.g. `crypto.randomUUID()`) client-side once per attempt, never derived from `amount`/account fields. Full rationale + worked scenarios: `04-SRS.md` §7a / FR-1.1a.
- Pagination: query params `page` (0-based, default `0`), `size` (default `20`, max `100`), response wrapped in a `Page` envelope.
- All error responses share the shape:
```json
{
  "errorCode": "INVALID_AMOUNT",
  "message": "Amount must be greater than 0",
  "timestamp": "2026-07-30T14:23:01Z",
  "path": "/api/v1/payments"
}
```

## 2. Endpoint Summary

| Method | Path | Purpose |
|---|---|---|
| POST | `/payments` | Create a new payment |
| GET | `/payments/{id}` | Get a payment by ID |
| GET | `/payments` | List/search/filter payments (paginated) |
| GET | `/payments/{id}/history` | Get full status-transition audit trail |
| POST | `/payments/{id}/validate` | Explicitly trigger `CREATED→VALIDATED` transition |
| POST | `/payments/{id}/send` | Explicitly trigger `VALIDATED→SENT` transition |
| POST | `/payments/{id}/complete` | Explicitly trigger `SENT→COMPLETED` transition |

> Note (MEM-007): payments **auto-progress** through the full lifecycle immediately on creation for demo speed. The explicit `/validate`, `/send`, `/complete` endpoints exist for testing, manual control, and demonstrating rejection of illegal transitions — the frontend's primary flow only needs `POST /payments` + polling/`GET`, not manual calls to these.

---

## 3. `POST /payments` — Create Payment

**Headers:** `Idempotency-Key: <string>` *(optional but recommended)*

**Request Body:**
```json
{
  "amount": 250.00,
  "currency": "USD",
  "sourceAccount": "ACC1000001",
  "destinationAccount": "ACC2000002",
  "reference": "Invoice #4471"
}
```

**Field rules:**
| Field | Type | Required | Rules |
|---|---|---|---|
| `amount` | number | yes | > 0, <= 1,000,000, max 2 decimal places |
| `currency` | string | yes | 3-letter ISO code, one of supported list (e.g. USD, EUR, GBP) |
| `sourceAccount` | string | yes | 8–20 alphanumeric chars |
| `destinationAccount` | string | yes | 8–20 alphanumeric chars, must differ from `sourceAccount` |
| `reference` | string | no | max 255 chars |

**Responses:**

- **`201 Created`** — new payment created (regardless of whether it ends up `COMPLETED` or `FAILED` after auto-progression — the *resource* was successfully created; the body reflects final status).
  - Header: `Location: /api/v1/payments/{id}`
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 250.00,
  "currency": "USD",
  "sourceAccount": "ACC1000001",
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
- **`400 Bad Request`** — field-level validation failure (`VALIDATION_FAILED`, `INVALID_AMOUNT`, `INVALID_CURRENCY`, `INVALID_ACCOUNT`).

---

## 4. `GET /payments/{id}` — Get Payment by ID

**Responses:**
- **`200 OK`**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 250.00,
  "currency": "USD",
  "sourceAccount": "ACC1000001",
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

---

## 5. `GET /payments` — List / Search / Filter

**Query params:**
| Param | Type | Notes |
|---|---|---|
| `status` | string | optional, one of `CREATED, VALIDATED, SENT, COMPLETED, FAILED` |
| `search` | string | optional, matches against `reference` or `id` (partial match) |
| `page` | int | default 0 |
| `size` | int | default 20, max 100 |
| `sort` | string | default `createdAt,desc` |

**Example:** `GET /payments?status=FAILED&page=0&size=20`

**Response `200 OK`:**
```json
{
  "content": [
    { "id": "...", "amount": 250.00, "currency": "USD", "status": "FAILED", "errorCode": "NETWORK_ERROR", "createdAt": "..." }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 6. `GET /payments/{id}/history` — Status History / Audit Trail

**Response `200 OK`:**
```json
[
  { "previousStatus": null, "newStatus": "CREATED", "errorCode": null, "errorMessage": null, "triggeredBy": "CLIENT", "occurredAt": "2026-07-30T14:23:01Z" },
  { "previousStatus": "CREATED", "newStatus": "VALIDATED", "errorCode": null, "errorMessage": null, "triggeredBy": "SYSTEM", "occurredAt": "2026-07-30T14:23:01Z" },
  { "previousStatus": "VALIDATED", "newStatus": "FAILED", "errorCode": "NETWORK_ERROR", "errorMessage": "Simulated network failure", "triggeredBy": "SYSTEM", "occurredAt": "2026-07-30T14:23:02Z" }
]
```
- **`404 Not Found`** if the payment ID doesn't exist (`PAYMENT_NOT_FOUND`).

---

## 7. `POST /payments/{id}/validate` | `/send` | `/complete` — Explicit Transitions

No request body. Behave exactly like the corresponding automatic step (§3), but callable independently — primarily for testing/demo of the state machine's guard logic.

**Responses:**
- **`200 OK`** — updated `PaymentResponse` (same shape as §4).
- **`400 Bad Request`** — illegal transition attempted:
```json
{ "errorCode": "INVALID_STATUS_TRANSITION", "message": "Cannot transition from COMPLETED to VALIDATED", "timestamp": "...", "path": "/api/v1/payments/.../validate" }
```
- **`404 Not Found`** — unknown payment ID.
- **`409 Conflict`** *(concurrency, NFR-8)* — optimistic lock conflict if two requests race:
```json
{ "errorCode": "PROCESSING_ERROR", "message": "Payment was concurrently modified, please retry", "timestamp": "...", "path": "..." }
```

---

## 8. Error Code → HTTP Status Reference (adopted from brief Appendix B — final)

| Error Code | HTTP Status | Triggered by |
|---|---|---|
| VALIDATION_FAILED | 400 | Generic field-level validation |
| INVALID_ACCOUNT | 400 | Bad account format / same source & destination |
| INVALID_CURRENCY | 400 | Unsupported currency code |
| INVALID_AMOUNT | 400 | Amount <= 0, > max, or wrong decimal precision |
| DUPLICATE_PAYMENT | *(reserved, not used by default — see MEM-006)* | Would apply only if strict-reject mode chosen instead of 200+existing |
| INVALID_STATUS_TRANSITION | 400 | Illegal transition attempted |
| PAYMENT_NOT_FOUND | 404 | Unknown payment ID |
| PROCESSING_ERROR | 500 (or 409 for lock conflicts) | Internal/simulated processing error, optimistic lock conflict |
| NETWORK_ERROR | 503 *(reflected in payment body as-is; HTTP response for the *triggering* explicit endpoint call itself is 200 since the transition to FAILED succeeded as an operation)* | Simulated network failure |
| INSUFFICIENT_FUNDS | 400 | Reserved — not actively triggered in core scope (no real balance ledger), available for a stretch enhancement |

> **Important distinction:** For **explicit transition endpoints**, a "successful transition to FAILED" is still an HTTP `200` (the API call itself succeeded — it did what was asked: attempt the transition, and correctly recorded a failure). HTTP error statuses (`400/404/409/500/503`) are reserved for when the **API call itself** couldn't be honored (bad request shape, illegal transition, not found, conflict, internal fault) — not for "the payment's business outcome was a failure." This distinction is called out explicitly to avoid frontend confusion.

## 9. Frontend Integration Notes

- Use `GET /payments/{id}` (short poll every 1–2s, or just once since auto-progression is synchronous in Sprint 1) to reflect final status after `POST /payments`.
- Status badge color mapping (Appendix D): 🟢 `COMPLETED`, 🟡 `CREATED`/`VALIDATED`/`SENT`, 🔴 `FAILED`.
- **Idempotency-Key generation rule (see `04-SRS.md` §7a for full scenario table):**
  1. Generate the key (`crypto.randomUUID()`) **once**, at the moment the Submit button is clicked — store it in component state/ref tied to that submission attempt.
  2. **Disable the Submit button immediately** on click (before the request even fires) — this is the primary defense against double-click generating two different keys for what the user intended as one action.
  3. If the request fails with a **retryable** error (network/timeout) and you show a "Retry" action, **reuse the same stored key** for the retry.
  4. On success, or on a **non-retryable** failure the user must fix (e.g. validation error — they'll edit the form and resubmit), **discard the key** — re-enable the form for a fresh submission, which will generate a **new** key next time.
  5. Do **NOT** compute the key from form field values (no hashing amount+accounts) — that would incorrectly block a user's legitimate, separate repeat payment. See worked scenario #4 in `04-SRS.md` §7a.


