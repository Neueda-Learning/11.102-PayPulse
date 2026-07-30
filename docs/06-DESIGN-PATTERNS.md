# 🎨 DESIGN-PATTERNS.md — Pattern Selection & Rationale

Related: `05-ARCHITECTURE.md`. This doc justifies **why** each pattern was chosen — useful both for design integrity and for the presentation ("how did you approach it").

| # | Pattern | Where Used | Why |
|---|---|---|---|
| 1 | **State Pattern** | `StatusTransitionEngine` + one class per status (`CreatedState`, `ValidatedState`, `SentState`, `CompletedState`, `FailedState`) implementing a common `PaymentState` interface with `validate()`, `send()`, `complete()`, `fail()` methods that know their own legal next-states. | The payment lifecycle IS a textbook finite state machine (brief literally suggests this in Considerations). Encapsulating transition rules inside state classes (rather than a giant if/else in the service) makes illegal transitions structurally hard to write, keeps each state's rules isolated/testable, and is easy to explain/demo live — a strong "design pattern" story for the presentation. |
| 2 | **Strategy Pattern** | `AmountValidator`, `CurrencyValidator`, `AccountValidator` implementing a common `PaymentValidator` interface. | Each validation rule (Appendix C) varies independently and may change/extend over time (e.g. add `InsufficientFundsValidator` later). Strategy lets us add/remove/reorder rules without touching existing ones — Open/Closed Principle. |
| 3 | **Chain of Responsibility** | `ValidationChain` — wraps an ordered list of `PaymentValidator` strategies, runs them in sequence, collects/stops on first (or all) failures. | Combines cleanly with Strategy: the *chain* decides execution order & short-circuit behavior, each *link* (strategy) decides one rule. Also matches Spring's natural `List<PaymentValidator>` autowiring (Spring injects all beans of the interface type in a `List` — trivial to wire up). |
| 4 | **Repository Pattern** | `PaymentRepository`, `PaymentStatusHistoryRepository` (Spring Data JPA interfaces). | Explicitly suggested by the brief (Appendix G). Decouples business logic from persistence tech — service layer talks to an interface, not to SQL/Hibernate directly. Already "free" via Spring Data, but the discipline of coding against the repository interface (not `EntityManager` directly) is what matters. |
| 5 | **DTO + Mapper Pattern (MapStruct)** | `CreatePaymentRequest`, `PaymentResponse`, `PaymentHistoryResponse` DTOs + `PaymentMapper` interface. | Prevents leaking JPA entities across the API boundary (avoids lazy-loading serialization bugs, decouples API contract from DB schema so either can evolve independently). Also gives the frontend a stable, well-documented shape regardless of internal refactors. |
| 6 | **Builder Pattern** | `Payment.builder()...build()` (Lombok `@Builder`) for constructing `Payment` entities with many optional fields. | Clearer than a telescoping constructor; matches JPA entity conventions well; especially useful once we have ~10 fields on `Payment`. |
| 7 | **Idempotency via "Check-then-Act" + DB Unique Constraint (belt-and-braces)** | `IdempotencyService.findExisting(key)` checked before insert; `idempotency_key` column has a DB-level `UNIQUE` constraint as a safety net against race conditions. | Not a classic GoF pattern, but a well-known **idempotent receiver** pattern from enterprise integration. Application-level check gives a friendly `200 + existing payment` response; DB constraint guarantees correctness even under a race (two near-simultaneous requests) — the DB-level violation is caught and translated to the same "return existing" response. |
| 8 | **Optimistic Locking (`@Version`)** | `Payment.version` field. | Standard JPA pattern for NFR-8 (basic concurrency safety) without the complexity/cost of pessimistic row locks — appropriate for a low-contention single-user training system, while still being a legitimate answer to "how do you handle concurrent updates?" in the presentation. |
| 9 | **Global Exception Handler / Controller Advice** | `GlobalExceptionHandler` (`@RestControllerAdvice`). | Centralizes API-under-failure concerns (NFR-6) — one place mapping domain exceptions → consistent `ApiError` JSON + correct HTTP status, instead of scattering try/catch across controllers. |
| 10 | **Dependency Injection (constructor-based)** | Everywhere (Spring beans). | Testability (can mock collaborators easily in unit tests) + explicit dependencies (no hidden field injection) — mandated in `03-SKILLS.md` coding standards. |
| 11 | *(Stretch, Appendix E)* **Observer/Event-driven (Spring `ApplicationEventPublisher`)** | Publish a `PaymentStatusChangedEvent` on every transition; a listener could later add notifications/webhooks without touching core service code. | Only if time permits — mentioned here so the design is already *shaped* to accept it later (Open/Closed) without a rewrite. Not required for Sprint 1. |

## Why this combination (not something else)?

- We deliberately picked **well-established, still industry-current** patterns (State, Strategy, Chain of Responsibility, Repository, DTO/Mapper, Builder, Optimistic Locking) rather than chasing something exotic — the brief rewards a **solid, explainable, testable design** over cleverness.
- We considered **Spring Statemachine** (a real library) as an alternative to hand-rolling the State pattern. **Decision: hand-roll it** for Sprint 1 — a dependency-free `PaymentState` interface is simpler to reason about, easier to unit-test without Spring context, and easier to explain in the presentation/diagrams than a library's internal machinery. We can swap to Spring Statemachine later as a "we evaluated X and chose to hand-roll for clarity" talking point — genuinely a legitimate architectural decision either way.
- We considered a **CQRS-style split** (separate read/write models) — rejected as over-engineering for this data size/complexity; a straightforward Repository + Service model is sufficient and more agile-appropriate.
- We considered an **event-sourcing** approach for the audit trail (rebuild state purely from events) — elegant in theory, but higher complexity/risk for a 7-day deadline. Instead we use a simpler **"state column + append-only history table"** model which achieves the audit requirement (NFR-2) with far less machinery, while still being conceptually adjacent to event sourcing if we want to mention it as a "considered alternative" in the presentation.

## Illustrative snippet (conceptual, not final code) — State + Strategy interplay

```java
public interface PaymentState {
    PaymentStatus name();
    Payment validate(Payment payment, ValidationChain chain); // CREATED -> VALIDATED/FAILED
    Payment send(Payment payment);                             // VALIDATED -> SENT/FAILED
    Payment complete(Payment payment);                         // SENT -> COMPLETED/FAILED
    default Payment illegalTransition() {
        throw new InvalidStatusTransitionException(...);
    }
}

public interface PaymentValidator {
    Optional<ValidationError> validate(Payment payment); // Strategy
}

public class ValidationChain {
    private final List<PaymentValidator> validators; // Spring injects all impls
    public List<ValidationError> runAll(Payment payment) { ... }
}
```

This will be formalized precisely in the **Class Diagram** (Phase 3, next).

