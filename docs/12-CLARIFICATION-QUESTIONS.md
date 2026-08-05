# ❓ CLARIFICATION-QUESTIONS.md — Questions for Instructor ("Customer")

> Purpose: Before/while building, get these answered by your instructor (acting as customer) in your next check-in. Each question includes **our current default/assumption** (so we're not blocked while waiting) and **why it matters**. Update `02-MEMORY.md` once answered (mark the relevant MEM entry Confirmed/Changed).

> **Updated 31 Jul 2026** — the first customer meeting resolved Q1, Q3, Q5 below (now struck through, answers logged in MEM-017/018/019/020). New open items added at the end (§ New Questions).
> **Updated 05 Aug 2026** — Core shipped; new V2 questions (Q15–Q19) added at the end for the Good-to-Have/Future wave (MEM-023–034).

---

## 🔴 High Priority (affects core design — ask first)

### Q1. Idempotent duplicate submission behavior
- **Question:** If a client submits the same `Idempotency-Key` twice, should we (a) return the existing payment with `200 OK`, or (b) reject the second request with `409 Conflict` + `DUPLICATE_PAYMENT`?
- **Our default (MEM-006):** (a) — return `200` + existing payment.
- **Why it matters:** Changes API contract + frontend retry logic. Brief's Appendix B literally lists `DUPLICATE_PAYMENT → 409`, but the Considerations section explicitly asks "reject or return existing?" — so it's ambiguous by design, we should confirm.
- **Status:** Still open, not addressed in the 31 Jul meeting — bring to next check-in.

### Q2. Auto-progression vs. explicit control of lifecycle
- **Question:** Should a payment automatically progress `CREATED → VALIDATED → SENT → COMPLETED` immediately after creation (simulated), or should each transition require an explicit client/API call?
- **Our default (MEM-007):** Both — auto-progress for the main demo flow, but also expose explicit `/validate`, `/send`, `/complete` endpoints for testing/control.
- **Why it matters:** Affects perceived realism of the demo (real payments don't complete instantly) and how much of the "retry/error handling" story is visible to the user.
- **Status:** Still open, not addressed in the 31 Jul meeting — bring to next check-in.

### ~~Q3. Account existence validation~~ ✅ RESOLVED 31 Jul 2026 (MEM-017)
- **Question:** Do source/destination accounts need to exist in some account registry/master table, or is format-only validation (e.g. regex pattern) sufficient?
- **Resolved answer:** **Yes — a real `Account` entity is required.** The single operator owns multiple accounts (e.g. INR + USD), and the UI must let them **select one as the payment source** at creation time. This applies to the **source** account only; the **destination** account (an external party) remains format-only validated.
- **Impact:** `Account` entity + Flyway migration + seed data + `GET /accounts`/`GET /accounts/{id}` endpoints added to core scope. See `04-SRS.md` FR-0, `08-UML-CLASS-DIAGRAM.md`, `openapi.yaml`.

### Q4. Should validation/processing simulate realistic delay, or be instant?
- **Question:** Is it acceptable for the whole lifecycle to resolve within the same HTTP request/response (synchronous, instant), or would you like to see a visible "in progress" state (e.g. a few seconds' delay, polling in the UI)?
- **Our default:** Synchronous/instant for Sprint 1 (simplest, deterministic, easiest to test); can add artificial delay + polling as a Sprint 2 enhancement for demo realism.
- **Why it matters:** Affects whether the frontend needs polling/websockets or just a single request-response.
- **Status:** Still open, not addressed in the 31 Jul meeting — bring to next check-in.

---

## 🟡 Medium Priority (affects scope/UX, but not core architecture)

### ~~Q5. Supported currencies~~ ✅ RESOLVED 31 Jul 2026 (MEM-018)
- **Question:** Is a small hardcoded list (USD, EUR, GBP) sufficient, or do you expect a broader/configurable ISO 4217 set?
- **Resolved answer:** **INR and USD only.** No cross-currency conversion between them.
- **Impact:** `currency` field/enum updated everywhere (SRS, API design, openapi.yaml); each `Account` is denominated in exactly one of the two.

### Q6. Failure simulation strategy
- **Question:** Should simulated failures at SEND/COMPLETE stages be random (e.g. 10% chance), deterministic based on input (e.g. a specific "magic" account/amount triggers failure, for reliable demo/testing), or configurable via an admin/test endpoint?
- **Our default (leaning):** Deterministic triggers for reliable testing/demo (e.g. a reserved test account number always fails) PLUS a small random chance for realism — combination gives us both reliable test cases and a "surprise" element for the live demo.
- **Status:** Still open — bring to next check-in.

### Q7. Search behavior specifics
- **Question:** For "search by payment ID or reference" (Appendix D), should search be exact-match or partial/fuzzy match? Case-sensitive?
- **Our default:** Partial, case-insensitive match on `reference`; exact match on `id` (UUID).
- **Status:** Still open — bring to next check-in.

### Q8. Pagination defaults
- **Question:** Any preference on default page size or sort order for the payment list view?
- **Our default:** `size=20`, sorted `createdAt DESC` (newest first).
- **Status:** Still open — low risk.

---

## 🟢 Lower Priority (nice to confirm, low risk either way)

### Q9. Which Appendix E "advanced feature" would most impress in the presentation?
- **Question:** If we have spare time after core+enhancements, which stretch feature would the instructor/stakeholders find most valuable to see: concurrency handling demo, basic reporting/analytics, payment cancellation, or something else?
- **Why it matters:** Helps us prioritize Sprint 3 (stretch) work in the final 1–2 days rather than guessing.
- **Note (31 Jul 2026):** Analytics/reporting has now moved from "stretch candidate" to **core scope** (MEM-019), so this question is really about the *remaining* stretch candidates now: cancellation, concurrency demo, or something else.

### Q10. Presentation audience/depth
- **Question:** Should the demo emphasize the design patterns/architecture (more technical) or the user-facing flow (more product-demo style), given the audience may include non-technical stakeholders?
- **Why it matters:** Shapes how we structure the live demo and slide content.

### Q11. Git platform & repo structure preference
- **Question:** Confirm which Git platform to use (GitHub/GitLab/Bitbucket/internal) and whether backend+frontend should be one monorepo or two separate repositories.
- **Our default:** Monorepo (`backend/`, `frontend/`, `docs/`) for simplicity in a 4-person, 7-day project — easier to keep docs/code in sync; can split later if needed.

---

## 🆕 New Questions (raised by the 31 Jul 2026 customer meeting itself)

### Q12. Is the 40,000 req/min figure a system-wide ceiling or per-client?
- **Question:** Should the rate limiter treat 40,000 req/min as the **total system-wide** capacity, or as a **per-client** allowance (meaning the true system capacity could be a multiple of that)?
- **Our default (MEM-020):** System-wide ceiling; per-client buckets are a smaller fair-share sub-limit under that ceiling.
- **Why it matters:** Changes the sizing of both the global and per-client Bucket4j buckets.

### Q13. Should `Account` carry a real balance (enabling `INSUFFICIENT_FUNDS`)?
- **Question:** Is a `balance` field expected on `Account` (enforcing real-looking "can't overdraw" behavior), or is `Account` purely an identity/selection concept for this training system?
- **Our default:** No balance field in Sprint 1 — `INSUFFICIENT_FUNDS` remains a reserved, unused error code (as in the original brief).
- **Why it matters:** Adding balance means also modeling debits/credits and would meaningfully increase scope — worth confirming before committing engineering time.

### Q14. Any preference on rate-limit library/infrastructure (in-app vs. gateway)?
- **Question:** Is an in-application rate limiter (Bucket4j, as planned) acceptable, or is there an expectation of a dedicated API Gateway/reverse-proxy-level limiter (e.g. nginx, Kong) for the presentation?
- **Our default:** In-app Bucket4j for Sprint 1 (no extra infra dependency); documented as upgradeable to a gateway-level limiter later.

---

## ✅ Action Item
Bring this list to the next instructor check-in. Log every answer as a new dated entry in `02-MEMORY.md` (e.g. `MEM-023 | Q1 answer confirmed`) so decisions stay traceable.

---

## 🆕 V2 Questions (raised 05 Aug 2026, during the Good-to-Have/Future wave)

### Q15. Is a meaningful demo window needed for Payment Cancellation (#18)?
- **Question:** Core's auto-progression (MEM-007) resolves `CREATED→VALIDATED` synchronously and near-instantly. Should V2 add a small artificial delay to the auto-progression specifically so `POST /payments/{id}/cancel` has a realistic, demoable window to actually catch a payment in `CREATED`? Or is it acceptable that cancellation is demonstrated primarily via the explicit `/validate` control endpoint (i.e., don't call `/validate` yet, cancel first)?
- **Our default (MEM-029):** Keep auto-progression synchronous (no artificial delay) — demo cancellation using the explicit control endpoints, which already provide a reliable `CREATED`-only window. Revisit only if the instructor specifically wants to see cancellation "race" the automatic flow.
- **Why it matters:** Affects whether cancellation needs a companion config toggle (`paypulse.processing.simulated-delay-ms`) added in this wave or not.

### Q16. Does Multi-Currency Conversion (#20) need real cross-currency settlement, or is display-only sufficient?
- **Question:** The feature list marks full FX conversion (paying an INR account's funds out as USD) as **Future/post-MVP**. V2 ships only a **display-only** rate hint (MEM-031). Is that sufficient for this delivery, or is there appetite/time to attempt real conversion (with all the rounding/reconciliation complexity that implies)?
- **Our default:** Display-only for this wave; full conversion remains explicitly out of scope pending confirmation.
- **Why it matters:** Real conversion is a significantly larger scope item (live rate feed, FX-loss accounting, reconciliation) — worth confirming before any engineering time is spent beyond the display hint.

### Q17. Notification provider — real SMTP or a mock/log-only sink for the presentation?
- **Question:** Is a real SMTP provider (e.g. a sandbox Mailtrap/SendGrid account) expected to be wired up for the live demo, or is logging to `notification_log` (with `EmailService` mocked/no-op) sufficient to demonstrate the wiring/audit-trail mechanics?
- **Our default:** Use whatever `EmailService` implementation already exists in the codebase (see `notification/service/EmailService.java`) as-is; if it's already a real SMTP client, no change needed; if it's a stub/log-only implementation, that's acceptable for a training-project demo.
- **Why it matters:** Avoids last-minute scrambling to provision real email credentials if a stub is already acceptable.

### Q18. CSV export row cap — is 50,000 rows an acceptable default?
- **Question:** Is `paypulse.export.max-rows = 50,000` (MEM-032) a sensible default, or should it be configured differently for the demo dataset size?
- **Our default:** 50,000 — comfortably above any realistic demo dataset, low risk either way.

### Q19. SSE reconnection/timeout — any specific reverse-proxy constraints to plan around?
- **Question:** `frontend/nginx/nginx.conf` will need `proxy_buffering off` (and a generous/disabled read timeout) on the `/api/v1/analytics/stream` route for SSE to work through the container's nginx proxy. Is the deployment environment (Docker Compose, per `docker-compose.yml`) the final target, or is there a different reverse proxy in the actual demo environment that needs the same treatment?
- **Our default:** Assume `docker-compose.yml`'s nginx is the target; configure it accordingly as part of the SSE task (M1).

---

