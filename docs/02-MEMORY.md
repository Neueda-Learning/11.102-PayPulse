# 🧠 MEMORY.md — Decision Log (ADR-lite)

> Purpose: Every meaningful decision (architecture, pattern, scope cut, naming, data model change) gets logged here with **date, decision, rationale, alternatives considered**. This prevents "why did we do it this way?" amnesia and lets a new teammate or instructor understand our reasoning fast.
>
> Format per entry: `#ID | Date | Title | Decision | Rationale | Alternatives Considered | Status`

---

### MEM-001 | 2026-07-30 | Adopt staged, gated delivery process
- **Decision:** Work will proceed in explicit phases (Groundwork → SRS → Architecture/Patterns → UML → API Design → Implementation Sprints → Frontend → Testing → Presentation), each phase gated by explicit confirmation before moving to the next.
- **Rationale:** Deadline is tight (7 days); avoids rework from designing/coding in the wrong direction. Matches "industrial" request from team lead.
- **Alternatives Considered:** Jump straight to coding (rejected — high risk of throwaway work); full waterfall with heavy SRS (rejected — too slow for 7-day window).
- **Status:** ✅ Accepted

### MEM-002 | 2026-07-30 | Documentation set: roadmap, context, memory, skills
- **Decision:** Maintain `docs/00-ROADMAP.md`, `docs/01-CONTEXT.md`, `docs/02-MEMORY.md`, `docs/03-SKILLS.md` as living project meta-docs, versioned in git alongside code.
- **Rationale:** Requested explicitly; also standard practice for onboarding + traceability, useful in the final presentation ("how did you approach it").
- **Alternatives Considered:** Single monolithic README (rejected — harder to keep organized as project grows).
- **Status:** ✅ Accepted

### MEM-003 | 2026-07-30 | Database: MySQL (via MySQL Workbench on team VM)
- **Decision:** Use **MySQL** as the persistent DB, managed/inspected via MySQL Workbench on the team's VM. Spring Data JPA + Flyway (MySQL dialect/syntax) for schema migrations. H2 in MySQL-compatibility mode for fast local unit tests; Testcontainers-MySQL optional for higher-fidelity integration tests.
- **Rationale:** Confirmed by team — this is the DB tooling already available/trained on in the VM environment. Avoids setup friction under a tight deadline.
- **Alternatives Considered:** PostgreSQL (initial provisional default, MEM-003 original) — superseded now that team confirmed MySQL is the trained/available tool. MongoDB — rejected (relational audit-trail/status-history modeling fits SQL better).
- **Status:** ✅ Accepted (supersedes original provisional entry)

### MEM-004 | 2026-07-30 | Minimum viable Payment fields (initial)
- **Decision:** Start with: `id, amount, currency, sourceAccount, destinationAccount, status, createdAt, updatedAt, idempotencyKey`. Reference/description optional field added early since it's cheap and UI wants it.
- **Rationale:** Brief explicitly recommends starting minimal (id, amount, currency, status) then growing; account fields are required for the validation rules (Appendix C) and UI (Appendix D), so included from the start rather than bolted on later.
- **Alternatives Considered:** Even more minimal (id, amount, currency, status only) — rejected because account fields are needed almost immediately for validation/idempotency demo value, and adding them later means a migration + retrofit of validation logic.
- **Status:** ⏳ To be finalized in SRS (Phase 1)

### MEM-005 | 2026-07-30 | Team of 4 — hybrid role split
- **Decision:** Team confirmed at **4 members (M1–M4)**. Strategy: all 4 work backend first (Day 1–4), split **by vertical module** (not by layer) — M1: entity/schema/idempotency, M2: state machine/audit trail, M3: create-payment/validation/error handling, M4: get/list endpoints + OpenAPI contract. From Day 5, split 2-and-2: M1+M2 continue backend hardening/enhancements; M3+M4 move to React frontend (they already own the API contract + create/detail endpoints, giving them the most context for those screens).
- **Rationale:** Matches brief's own suggested approaches ("2 on backend/1 on UI" vs "everyone on backend until basic system works") — we start with full-team-on-core to get the lifecycle working fast (highest risk item), then parallelize FE/BE once the contract is stable. Vertical-slice ownership (vs horizontal layers) avoids blocking on each other and merge conflicts.
- **Alternatives Considered:** Split 2 backend / 2 frontend from Day 1 (rejected — frontend would have nothing real to build against until API is stable; risks idle/rework time under a 7-day deadline). Layer-based split, e.g. "everyone on controllers" (rejected — high merge-conflict risk, unclear individual accountability).
- **Status:** ✅ Accepted — update with real names (replacing M1–M4) once assigned.

### MEM-006 | 2026-07-30 | Idempotent duplicate submission → 200 + existing payment (not 409)
- **Decision:** When a client submits a `POST /payments` with an `idempotencyKey` that already exists, the API returns **`200 OK`** with the existing payment resource (not `201`, not `409`).
- **Rationale:** Smoother client UX/retry-safety — the brief's Considerations section explicitly asks "reject or return existing?" and treats retries as a first-class concern (network retries should be safe/idempotent). Reserving `409 DUPLICATE_PAYMENT` strictly for a stricter interpretation if the instructor requests "reject" behavior instead — trivial one-line change if so.
- **Alternatives Considered:** `409 Conflict` with `DUPLICATE_PAYMENT` error code (brief's Appendix B literally maps this) — kept as a documented alternative; easy to switch. `201` with existing payment (rejected — misleading, implies a new resource was created).
- **Status:** ✅ Accepted (default) — **flag to confirm with instructor**; revert to 409 in 5 minutes if they prefer strict rejection.

### MEM-007 | 2026-07-30 | Expose BOTH auto-progression and explicit transition endpoints
- **Decision:** The system auto-progresses a payment through `CREATED → VALIDATED → SENT → COMPLETED` (or to `FAILED`) automatically/synchronously after creation for demo speed, but we ALSO expose explicit transition endpoints (e.g. `POST /payments/{id}/validate`, `/send`, `/complete`) so transitions can be triggered/tested independently and so the state machine's guard logic is directly demonstrable (good for sequence diagrams + live demo + testing).
- **Rationale:** Best of both worlds — smooth automatic demo flow for the "happy path" story, plus explicit control for testing invalid-transition rejection (Appendix F test scenarios need to *attempt* an illegal transition, which requires an addressable endpoint).
- **Alternatives Considered:** Auto-progression only (rejected — can't easily demo/test "invalid transition rejected" without an endpoint to call it on). Explicit-only, no auto-progression (rejected — every demo/test would need manual multi-step calls, slower to show the full lifecycle).
- **Status:** ✅ Accepted.

### MEM-008 | 2026-07-30 | Layered architecture (hexagonal-lite) + package-by-feature
- **Decision:** Adopt classic layered architecture (API → Service/Domain → Repository → DB) per brief's Appendix G, with clean dependency direction (downward only) and DTOs/mappers strictly at the API boundary. Package structure is **by-feature** (`payment/api`, `payment/domain`, `payment/service`, `payment/repository`) rather than purely by-layer.
- **Rationale:** Matches brief's own suggested architecture; package-by-feature keeps each team member's (M1–M4) work cohesive in separate subfolders, minimizing merge conflicts during the parallel Sprint 1 build.
- **Alternatives Considered:** Full hexagonal/ports-and-adapters (rejected — too much structural ceremony for a 7-day single-bounded-context project). Pure package-by-layer (`controllers/`, `services/`, `repositories/` at top level) — rejected, harder to keep each member's module self-contained.
- **Status:** ✅ Accepted.

### MEM-009 | 2026-07-30 | Hand-rolled State pattern (not Spring Statemachine) for lifecycle
- **Decision:** Implement the payment status lifecycle as a hand-rolled **State pattern** (`PaymentState` interface + one class per status), not the Spring Statemachine library.
- **Rationale:** Simpler to unit-test without a Spring context, easier to explain/diagram for the presentation, zero extra dependency risk under time pressure. Still a legitimate, explainable design-pattern choice.
- **Alternatives Considered:** Spring Statemachine library (rejected for Sprint 1 — adds learning curve/dependency risk under deadline; noted as a "considered alternative" for presentation talking points).
- **Status:** ✅ Accepted.

### MEM-010 | 2026-07-30 | Validation via Strategy + Chain of Responsibility; audit via append-only table (not event sourcing)
- **Decision:** Business-rule validators (amount/currency/account) are separate Strategy implementations run through a `ValidationChain` (Chain of Responsibility). Audit trail is a simple **status column + append-only `payment_status_history` table**, not full event sourcing.
- **Rationale:** Strategy+CoR gives Open/Closed extensibility for new rules with minimal ceremony and maps naturally onto Spring's `List<T>` bean injection. Append-only history table satisfies the audit NFR (NFR-2) with far less complexity/risk than event sourcing, appropriate for the deadline.
- **Alternatives Considered:** Event sourcing for full state reconstruction (rejected — high complexity/risk, not required by the brief). Single monolithic validator method (rejected — harder to extend/test individual rules).
- **Status:** ✅ Accepted.

### MEM-011 | 2026-07-30 | Testing planned per-layer before implementation
- **Decision:** Added `docs/07-TESTING-STRATEGY.md` — explicit test plan for unit (business logic), repository (`@DataJpaTest`), web/route (`@WebMvcTest`+MockMvc, every endpoint gets a success + failure case), and integration (`@SpringBootTest`) layers, mapped to Appendix F scenarios, with per-member (M1–M4) ownership baked in from the start.
- **Rationale:** Team explicitly flagged this gap before moving to diagrams — testing must be considered "for each and every thing" (routes, services, etc.), not bolted on afterward. Planning it now means test skeletons can be written alongside Sprint 1 code rather than retrofitted.
- **Alternatives Considered:** Defer testing strategy to Sprint 1 implementation time (rejected — risks tests being an afterthought/skipped under deadline pressure).
- **Status:** ✅ Accepted.

### MEM-012 | 2026-07-30 | UML diagrams completed (class, sequence, state) — Mermaid format
- **Decision:** `08-UML-CLASS-DIAGRAM.md`, `09-UML-SEQUENCE-DIAGRAMS.md` (7 scenarios: happy path, duplicate idempotency, validation failure, network failure, illegal transition, get/history, list/filter), `10-UML-STATE-DIAGRAM.md` — all in Mermaid syntax (renders natively on GitHub, no extra tooling needed for the team or presentation slides).
- **Rationale:** Mermaid chosen over PlantUML for zero-setup rendering in GitHub/most Markdown viewers and easy embedding directly into presentation slides (export as image via mermaid.live if needed for PowerPoint).
- **Alternatives Considered:** PlantUML (rejected as primary — requires a rendering plugin/server; kept as an option in `03-SKILLS.md` if anyone prefers it locally).
- **Status:** ✅ Accepted.

### MEM-013 | 2026-07-30 | Idempotency-Key delivered as HTTP header, not request body field
- **Decision:** Clients pass `Idempotency-Key: <string>` as a request header on `POST /payments`, not as a JSON body field.
- **Rationale:** Standard REST/HTTP convention (matches Stripe, PayPal, and other real payment APIs the training project mirrors); keeps the idempotency concern out of the domain payload, and lets the same body be resubmitted unchanged if a client needs to retry with a different key.
- **Alternatives Considered:** Body field `idempotencyKey` (was in the initial SRS draft) — superseded; still supported conceptually (mapped internally the same way) but the API contract now formally uses the header.
- **Status:** ✅ Accepted — supersedes SRS FR-1.1 body-field mention.

### MEM-014 | 2026-07-30 | Explicit transition endpoints return 200 even when the outcome is FAILED
- **Decision:** `POST /payments/{id}/validate|send|complete` return HTTP `200` whenever the transition *operation itself* completes (even if the payment's resulting status is `FAILED`). HTTP error codes (400/404/409/500/503) are reserved strictly for when the API call itself cannot be honored (bad request, illegal transition, not found, conflict, internal fault) — not for "business outcome was a failure."
- **Rationale:** Avoids conflating HTTP transport/request semantics with domain/business outcomes — a very common real-world API design mistake. Frontend can rely on `response.body.status` to know the payment outcome, and HTTP status to know if their *request* was even valid.
- **Alternatives Considered:** Return non-2xx (e.g. 422) whenever the resulting status is FAILED — rejected, muddies the "did my request work" vs "did the payment succeed" distinction and complicates frontend error handling.
- **Status:** ✅ Accepted.

### MEM-015 | 2026-07-30 | Consolidated clarification-questions doc + dedicated work-distribution doc
- **Decision:** Created `docs/12-CLARIFICATION-QUESTIONS.md` (all open questions across SRS/API design consolidated, prioritized High/Medium/Low, each with our current default so we're not blocked waiting on answers) and `docs/13-WORK-DISTRIBUTION.md` (standalone, detailed day-by-day task breakdown for M1–M4, expanding on the summary table originally in `03-SKILLS.md` §3).
- **Rationale:** Team requested these as explicit, dedicated deliverables rather than scattered across other docs — easier to hand directly to the instructor (questions doc) and to the team/Trello board (work distribution doc).
- **Status:** ✅ Accepted.

### MEM-016 | 2026-07-30 | Idempotency key clarified: scoped to submission attempt, NOT payment content
- **Decision:** Formally clarified (added FR-1.1a + §7a scenario table to `04-SRS.md`, expanded `11-API-DESIGN.md` §9) that the `Idempotency-Key` is generated **client-side, once per submission attempt**, immediately before the request fires (e.g. `crypto.randomUUID()`) — it is **never derived from payment field values** (amount/accounts/currency). A retried/duplicated *attempt* (double-click, network retry) reuses the same key and is deduplicated; a deliberate, separate payment — even with identical field values — gets a fresh key and is correctly created as a new, independent payment.
- **Rationale:** Team raised a valid concern — if the key were content-derived, a user legitimately paying the same account the same amount twice (e.g. two separate invoices) would be incorrectly blocked as a "duplicate." Scoping the key to the client's request-attempt lifecycle (matching how Stripe/PayPal-style APIs do it) solves the *accidental double-submit/retry* problem the brief actually asks about, without breaking legitimate repeat payments.
- **Alternatives Considered:** Server-derived/content-hash key (amount+accounts+time-window) — rejected, blocks legitimate repeats and is fragile (what time window is "the same" payment?). No idempotency key at all, relying purely on user discipline — rejected, brief explicitly asks us to consider double-submission.
- **Status:** ✅ Accepted.

---

*(New entries appended below as we progress through phases — do not edit history, only append or mark superseded.)*

