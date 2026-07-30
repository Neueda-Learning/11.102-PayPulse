# 🛠️ SKILLS.md — Tech Stack & Team Skill Baseline

> Purpose: Declare the fixed tech stack, the standards/conventions we'll follow, and the skill areas each team member should be comfortable with (or pair up to cover). Keeps everyone aligned and helps onboard fast.

## 1. Fixed Tech Stack

### Backend
- **Language:** Java (17+ LTS recommended)
- **Framework:** Spring Boot (3.x)
- **Build tool:** Maven (or Gradle — pick one, default: Maven for simplicity)
- **Persistence:** Spring Data JPA + Hibernate
- **Migrations:** Flyway (MySQL-compatible)
- **Database:** MySQL (via MySQL Workbench on team VM) — dev/prod; H2 in-memory (MySQL compatibility mode) for fast unit/integration tests — see MEM-003 (updated)
- **Validation:** Jakarta Bean Validation (`spring-boot-starter-validation`)
- **API Docs:** springdoc-openapi (Swagger UI)
- **Mapping:** MapStruct (Entity ↔ DTO)
- **Testing:** JUnit 5, Mockito, Spring Boot Test, Testcontainers (optional, for real MySQL integration tests)
- **Logging:** SLF4J + Logback (default with Spring Boot)
- **State machine (optional lib):** Spring Statemachine — OR a hand-rolled State pattern (decision pending in Phase 2)

### Frontend
- **Library:** React (18+), via Vite
- **Language:** TypeScript (recommended for API contract safety)
- **HTTP client:** Axios or fetch wrapper
- **Routing:** React Router
- **State/data-fetching:** React Query (TanStack Query) recommended for server state
- **Styling:** Team's choice (plain CSS/Tailwind/MUI) — decide when frontend phase starts
- **API mocking (for parallel work before backend is ready):** MSW (Mock Service Worker) or Swagger-driven mock, using `docs/openapi.yaml`

### Tooling / Process
- **Version control:** Git (feature branches + PRs, no direct pushes to `main`)
- **Diagrams:** Mermaid (renders in Markdown/GitHub) and/or PlantUML for UML
- **Task tracking:** Trello (or GitHub Projects) mirroring `docs/00-ROADMAP.md`
- **CI (stretch):** GitHub Actions — build + test on PR

## 2. Coding Standards

- Package-by-feature (e.g. `payment/`, `common/`) rather than package-by-layer-only, OR layered-by-feature hybrid — finalize in Architecture doc.
- DTOs at API boundary; never expose JPA entities directly in responses.
- Constructor injection only (no field `@Autowired`).
- All public API errors go through a `@ControllerAdvice` global exception handler → consistent error JSON shape.
- Meaningful commit messages; PRs reference roadmap task IDs.
- No business logic in controllers — controllers are thin, delegate to service layer.

## 3. Team & Role Split (Team of 4)

**Strategy:** Per the brief's own suggestion ("Everyone on backend until basic system is working" vs. split roles) — we go **hybrid**:
- **Sprint 0–1 (Core, Day 1–4):** All 4 on backend, split **by module** (not by layer) so each person owns a vertical slice end-to-end (entity → service → controller → tests) for their module. This avoids merge conflicts and idle time waiting on each other.
- **Sprint 2+ (Day 5 onward):** Split 2-and-2 — **2 continue hardening backend** (search/filter, validation chain, tests, stretch features) while **2 move to React frontend** (screens consuming the now-stable API contract from `docs/openapi.yaml`).
- Everyone reviews everyone's PRs (small team, keep it lightweight — quick look, not a bottleneck).

Assign real names against **M1–M4** below once you tell me who's who (I'll update this file):

| Member | Core Phase (Day 1–4) — Backend Module Ownership | Enhancement Phase (Day 5–7) |
|---|---|---|
| **M1** | Project skeleton, Git repo setup, `Payment` entity + Flyway migration (MySQL), DB schema, Idempotency handling (unique key + lookup) | Backend: validation rule chain (Strategy pattern), edge-case tests |
| **M2** | Status transition engine (State pattern) + `payment_status_history` audit table + history endpoint | Backend: search/filter by status, retry/simulated async processing, Swagger polish |
| **M3** | Create Payment endpoint + request validation (Bean Validation) + global exception handler + error code contract (Appendix B) | Frontend: Create Payment screen + Payment Details/status-history timeline |
| **M4** | Get Payment by ID + List Payments endpoint + OpenAPI/Swagger setup (`docs/openapi.yaml` first, so it can double as the API contract for M3's frontend teammates) | Frontend: Payment List screen (filter/search) + error/failed-payment view |

> Rationale: M4 owns the API contract early since list/detail endpoints are the simplest to spec first, giving the frontend-bound pair (M3/M4) something concrete to design screens against from Day 2 (Phase 4 gate) even before full implementation lands.

### Skill Area Coverage Matrix

| Skill Area | Why needed | Owner(s) |
|---|---|---|
| Spring Boot REST controllers & validation | Core API | M3 |
| Spring Data JPA / Flyway (MySQL) | Persistence & schema | M1 |
| State machine / status transition logic | Core business rule | M2 |
| Exception handling & error contract | Robust API under failure | M3 |
| Unit/integration testing (JUnit, Mockito) | Quality | All 4 (own module's tests) |
| React + component design | Frontend UX | M3 + M4 (Sprint 2+) |
| API contract / OpenAPI authoring | Enables FE/BE parallel work | M4 |
| Git workflow (branching/PRs) | Team collaboration hygiene | Everyone |
| UML/diagramming (Mermaid/PlantUML) | Design communication | Shared — drafted centrally (this doc set), reviewed by all 4 |


## 4. Learning/Reference Notes

- Payment status lifecycle is a textbook **finite state machine** — good candidate to demonstrate the **State design pattern** (or Spring Statemachine library) in the presentation.
- Idempotency is commonly solved via a unique DB constraint on `idempotency_key` + a lookup-before-insert check — simple, robust, and easy to explain live.
- Keep the audit trail as an **append-only** table (`payment_status_history`) — never update/delete rows, only insert — mirrors real-world compliance systems and is easy to demo.

