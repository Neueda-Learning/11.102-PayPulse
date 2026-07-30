# ❓ CLARIFICATION-QUESTIONS.md — Questions for Instructor ("Customer")

> Purpose: Before/while building, get these answered by your instructor (acting as customer) in your next check-in. Each question includes **our current default/assumption** (so we're not blocked while waiting) and **why it matters**. Update `02-MEMORY.md` once answered (mark the relevant MEM entry Confirmed/Changed).

---

## 🔴 High Priority (affects core design — ask first)

### Q1. Idempotent duplicate submission behavior
- **Question:** If a client submits the same `Idempotency-Key` twice, should we (a) return the existing payment with `200 OK`, or (b) reject the second request with `409 Conflict` + `DUPLICATE_PAYMENT`?
- **Our default (MEM-006):** (a) — return `200` + existing payment.
- **Why it matters:** Changes API contract + frontend retry logic. Brief's Appendix B literally lists `DUPLICATE_PAYMENT → 409`, but the Considerations section explicitly asks "reject or return existing?" — so it's ambiguous by design, we should confirm.

### Q2. Auto-progression vs. explicit control of lifecycle
- **Question:** Should a payment automatically progress `CREATED → VALIDATED → SENT → COMPLETED` immediately after creation (simulated), or should each transition require an explicit client/API call?
- **Our default (MEM-007):** Both — auto-progress for the main demo flow, but also expose explicit `/validate`, `/send`, `/complete` endpoints for testing/control.
- **Why it matters:** Affects perceived realism of the demo (real payments don't complete instantly) and how much of the "retry/error handling" story is visible to the user.

### Q3. Account existence validation
- **Question:** Do source/destination accounts need to exist in some account registry/master table, or is format-only validation (e.g. regex pattern) sufficient?
- **Our default (A-2 in SRS):** Format-only — no separate `Account` entity in core scope.
- **Why it matters:** If accounts must "exist," we need a seed/reference `accounts` table and an extra `INVALID_ACCOUNT` check path — a data-model change best made early, not retrofitted.

### Q4. Should validation/processing simulate realistic delay, or be instant?
- **Question:** Is it acceptable for the whole lifecycle to resolve within the same HTTP request/response (synchronous, instant), or would you like to see a visible "in progress" state (e.g. a few seconds' delay, polling in the UI)?
- **Our default:** Synchronous/instant for Sprint 1 (simplest, deterministic, easiest to test); can add artificial delay + polling as a Sprint 2 enhancement for demo realism.
- **Why it matters:** Affects whether the frontend needs polling/websockets or just a single request-response.

---

## 🟡 Medium Priority (affects scope/UX, but not core architecture)

### Q5. Supported currencies
- **Question:** Is a small hardcoded list (USD, EUR, GBP) sufficient, or do you expect a broader/configurable ISO 4217 set?
- **Our default:** Small hardcoded/config-driven list (A-6 in SRS).

### Q6. Failure simulation strategy
- **Question:** Should simulated failures at SEND/COMPLETE stages be random (e.g. 10% chance), deterministic based on input (e.g. a specific "magic" account/amount triggers failure, for reliable demo/testing), or configurable via an admin/test endpoint?
- **Our default (leaning):** Deterministic triggers for reliable testing/demo (e.g. a reserved test account number always fails) PLUS a small random chance for realism — combination gives us both reliable test cases and a "surprise" element for the live demo.

### Q7. Search behavior specifics
- **Question:** For "search by payment ID or reference" (Appendix D), should search be exact-match or partial/fuzzy match? Case-sensitive?
- **Our default:** Partial, case-insensitive match on `reference`; exact match on `id` (UUID).

### Q8. Pagination defaults
- **Question:** Any preference on default page size or sort order for the payment list view?
- **Our default:** `size=20`, sorted `createdAt DESC` (newest first).

---

## 🟢 Lower Priority (nice to confirm, low risk either way)

### Q9. Which Appendix E "advanced feature" would most impress in the presentation?
- **Question:** If we have spare time after core+enhancements, which stretch feature would the instructor/stakeholders find most valuable to see: concurrency handling demo, basic reporting/analytics, payment cancellation, or something else?
- **Why it matters:** Helps us prioritize Sprint 3 (stretch) work in the final 1–2 days rather than guessing.

### Q10. Presentation audience/depth
- **Question:** Should the demo emphasize the design patterns/architecture (more technical) or the user-facing flow (more product-demo style), given the audience may include non-technical stakeholders?
- **Why it matters:** Shapes how we structure the live demo and slide content.

### Q11. Git platform & repo structure preference
- **Question:** Confirm which Git platform to use (GitHub/GitLab/Bitbucket/internal) and whether backend+frontend should be one monorepo or two separate repositories.
- **Our default:** Monorepo (`backend/`, `frontend/`, `docs/`) for simplicity in a 4-person, 7-day project — easier to keep docs/code in sync; can split later if needed.

---

## ✅ Action Item
Bring this list to the next instructor check-in. Log every answer as a new dated entry in `02-MEMORY.md` (e.g. `MEM-015 | Q1 answer confirmed`) so decisions stay traceable.

