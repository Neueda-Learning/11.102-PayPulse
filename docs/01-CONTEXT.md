# 📌 CONTEXT.md — Project Context

> Purpose: A quick-load "briefing" doc. Anyone (new teammate, instructor, future-me) should be able to read this in 3 minutes and understand what we're building, why, and under what constraints.

## 1. What is this project?

A **Payments Processing REST API** (training project) that manages the full lifecycle of a financial payment — from creation, through validation and transmission, to completion or failure — with a complete, queryable audit trail of every status change. A React frontend will consume this API to let a single (unauthenticated) user create payments, track their status, view history, filter by status, and inspect errors on failed payments.

Source of truth for requirements: `payment_processing.md` (root of repo).

## 2. Why does it matter?

- It's a training project evaluated via a live presentation to instructors/stakeholders.
- It's explicitly meant to teach real API design, layered architecture, state machines, and clean data modeling — not just "make it work".
- Emphasis stated by the brief itself: **quality over quantity**, **start small**, **stay agile**.

## 3. Users / Stakeholders

- **End user (single, no auth):** creates payments, checks status/history.
- **Instructor (acting as "customer"):** defines/clarifies detailed requirements on request; evaluates the final presentation.
- **Team:** builds backend (Spring Boot) + frontend (React) collaboratively.

## 4. Scope

> **Updated 05 Aug 2026 (V2):** Core (§4a below) is complete and shipped. §4b lists the V2 wave now in progress. §4c is the remaining, still-deferred backlog. See `02-MEMORY.md` MEM-023 for the full rationale.

### 4a. In scope — Core (✅ shipped)
- Create payment
- Retrieve payment by ID
- List/search/filter payments by status
- Full status lifecycle: `CREATED → VALIDATED → SENT → COMPLETED`, with `FAILED` reachable from any stage
- Audit trail of all status transitions (timestamped)
- Idempotency handling for duplicate payment submissions
- Validation rules (amount, currency, account) with defined error codes
- Simulated payment processing (no real payment gateway/network integration)
- Swagger/OpenAPI documentation
- `Account` entity, multi-account source picker, INR/USD currencies
- KPI dashboard + basic analytics, ~40k req/min rate limiting, API security hardening
- Frontend covering the 5 prioritized user journeys (create, view status/details, view history, filter by status, view failure details)

### 4b. In scope — V2 (🚧 in progress, this wave)
- **#21 Notifications** — wire the already-implemented `notification` module into the payment lifecycle (email on created/completed/failed)
- **#16 Sortable Columns** — payment list table, click-to-sort
- **Load testing v2** — extend existing load tests to new V2 endpoints
- **Dashboard SSE** — replace the 30-second dashboard poll with a live `text/event-stream` push
- **Create Payment frontend cross-check** — audit against the API contract (see MEM-028 finding)
- **#18 Payment Cancellation** — cancel a `CREATED` payment before processing
- **#19 Payment Reversal** — reverse a `COMPLETED` payment via a new, linked offsetting payment
- **#13 Analytics/Trend View** — deepened beyond the existing dashboard bar chart
- **#14 CSV Export** — download the current filtered payment list
- **#17 Copy Payment ID / Deep Linking**
- **#20 Multi-Currency Conversion** — **display-only** FX hint (static rates); no real cross-currency settlement (see MEM-031)

### 4c. Explicitly Out of Scope (still deferred — revisit only if time remains)
- **#15 Configurable Failure Simulation** — not assigned this wave
- **#22 Batch/Bulk Payments**
- **#23 Recurring/Scheduled Payments**
- **#24 Full Authentication / multi-user / account ownership**
- **#25 Account Management Screen** (create/edit/deactivate accounts from the UI)
- Real payment network/gateway integration
- Real (non-display-only) multi-currency conversion / live FX rate feeds
- Optimistic/pessimistic concurrency locking beyond the existing `@Version` (already sufficient per NFR-8)

## 5. Constraints

- **Deadline: 6 Aug 2026** (~7 working days from kickoff) → hard constraint, drives scope discipline.
- **Backend stack:** Java, Spring, Spring Boot (fixed — no substitutions).
- **Frontend stack:** React (fixed).
- **Database:** **MySQL**, managed/inspected via **MySQL Workbench** on the team's VM (via Spring Data JPA); H2 (MySQL-compatibility mode) for fast local dev/tests.
- **Team size:** 4 members (M1–M4) — see `docs/03-SKILLS.md` §3 for role/module split.
- **No auth, single user** — do not over-engineer security.
- Must use Git with proper branching/PRs.

## 6. Key Domain Concepts

| Concept | Definition |
|---|---|
| Payment | A single financial transfer instruction: source account, destination account, amount, currency, status, plus metadata. |
| Status | One of `CREATED, VALIDATED, SENT, COMPLETED, FAILED`. Transitions are constrained (state machine). |
| Idempotency Key | Client-supplied key to detect/prevent duplicate payment submissions. |
| Audit Trail / Status History | Immutable, timestamped log of every status transition a payment undergoes, including error details on failure. |
| Error Code | Programmatic code (see Appendix B of brief) returned to client on failure, mapped to an HTTP status. |

## 7. Definition of Done (per increment)

A feature is "done" when:
1. Code implemented following agreed architecture/patterns.
2. Unit + (where relevant) integration tests pass.
3. API documented in OpenAPI/Swagger.
4. Manually verified via Swagger UI or frontend.
5. Committed via PR with review (even self-review if solo) referencing the roadmap task.

## 8. Living Document Note

This file, `memory.md`, and `skills.md` are **living documents**. Update them as decisions evolve — don't let them go stale.

