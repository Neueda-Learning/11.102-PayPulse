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

### In scope (Core)
- Create payment
- Retrieve payment by ID
- List/search/filter payments by status
- Full status lifecycle: `CREATED → VALIDATED → SENT → COMPLETED`, with `FAILED` reachable from any stage
- Audit trail of all status transitions (timestamped)
- Idempotency handling for duplicate payment submissions
- Validation rules (amount, currency, account) with defined error codes
- Simulated payment processing (no real payment gateway/network integration)
- Swagger/OpenAPI documentation
- React frontend covering the 5 prioritized user journeys (create, view status/details, view history, filter by status, view failure details)

### Explicitly Out of Scope (for now — revisit only if time remains, Appendix E)
- Authentication / multi-user / account ownership
- Real payment network/gateway integration
- Batch payments, scheduling/recurring payments
- Notifications (email/webhook)
- Reporting/analytics dashboards
- Multi-currency conversion / FX rates
- Payment reversal/cancellation
- Optimistic/pessimistic concurrency locking (nice-to-have if time permits in Sprint 2+)

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

