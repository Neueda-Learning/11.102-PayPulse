# M2 Implementation / Update Checklist

Purpose: track only the scope owned by M2 from `docs/13-WORK-DISTRIBUTION.md`, while also tracing M2 responsibilities back to the original design/spec documents. This checklist ignores implementation work that belongs to other members except where M2 must integrate with them or where the original docs make M2 behavior dependent on their contracts.

Status legend:
- [x] Implemented
- [ ] Not Implemented
- [~] Partially Implemented / Needs Fix

Last reviewed: 2026-08-04

Source documents cross-checked for this checklist:
- `docs/13-WORK-DISTRIBUTION.md`
- `docs/05-ARCHITECTURE.md`
- `docs/06-DESIGN-PATTERNS.md`
- `docs/07-TESTING-STRATEGY.md`
- `docs/08-UML-CLASS-DIAGRAM.md`
- `docs/09-UML-SEQUENCE-DIAGRAMS.md`
- `docs/10-UML-STATE-DIAGRAM.md`
- `docs/11-API-DESIGN.md`
- `docs/04-SRS.md`

---

## 1. M2 Feature Scope

### Owned features
- [~] Feature #4 — Automatic Payment Lifecycle Processing
- [~] Feature #6 — Payment Status History / Audit Trail
- [~] Resilience for lifecycle transitions (Circuit Breaker / Retry)

### Scope interpretation from original docs
- [x] M2 owns the payment lifecycle state machine
- [x] M2 owns explicit transition endpoints for manual/demo/test control
- [x] M2 owns append-only audit-trail persistence for transitions
- [x] M2 owns resilience behavior around simulated send/complete processing
- [x] M2 owns the frontend history/timeline component consumed by M4
- [~] M2 behavior is fully integrated into M3 automatic create-payment orchestration
- [ ] M2 behavior is fully reflected in DTO-only API responses per shared controller contract
- [ ] M2 publishes transition events / observer hooks if required by architecture/pattern docs

Notes:
- Core backend lifecycle pieces exist.
- Frontend timeline component is still missing.
- Automated flow is only fully complete once integrated with M3 create-payment orchestration.
- Some architecture/UML contracts are stricter than the current codebase and must be tracked explicitly.

---

## 2. Backend Domain and State Machine

### `backend/src/main/java/com/paypulse/payment/PaymentStatus.java`
- [x] Enum exists
- [x] Contains states: `CREATED`, `VALIDATED`, `SENT`, `COMPLETED`, `FAILED`
- [x] Has terminal-state helper (`isTerminal()`)
- [x] Matches the state set defined in `docs/10-UML-STATE-DIAGRAM.md`
- [x] Supports the transition table defined in `docs/04-SRS.md` FR-10
- [~] Package/location matches architecture/UML docs exactly

Current assessment:
- Implemented, but package differs from the work-distribution / architecture expectation (`payment/domain/PaymentStatus.java` vs actual `payment/PaymentStatus.java`).

---

### `backend/src/main/java/com/paypulse/payment/domain/PaymentStatusHistory.java`
- [x] Entity exists
- [x] Stores `paymentId`
- [x] Stores `previousStatus`
- [x] Stores `newStatus`
- [x] Stores `errorCode`
- [x] Stores `errorMessage`
- [x] Stores `triggeredBy`
- [x] Stores `occurredAt`
- [x] Supports ordered audit history retrieval
- [x] Supports append-only audit trail intent from `docs/04-SRS.md` NFR-2
- [~] Aligned with DTO/mapper-based API response strategy
- [~] Aligned with UML field expectations exactly (`UUID/BIGINT` nuance, response mapping not finalized)

Current assessment:
- Implemented as a persistence model.
- May need response mapping integration instead of exposing entity directly.

---

## 3. Backend State Classes

### `backend/src/main/java/com/paypulse/payment/service/states/PaymentState.java`
- [x] State contract exists
- [x] Defines `status()`
- [x] Defines `validate()`
- [x] Defines `send()`
- [x] Defines `complete()`
- [~] Matches UML/pattern docs exactly (`TransitionResult` / `fail()` method not present in current implementation)

### `backend/src/main/java/com/paypulse/payment/service/states/AbstractPaymentState.java`
- [x] Default invalid-transition behavior exists
- [x] Throws `InvalidStatusTransitionException` for unsupported actions

### `backend/src/main/java/com/paypulse/payment/service/states/CreatedState.java`
- [x] Exists
- [x] Allows `CREATED -> VALIDATED`
- [~] `CREATED -> FAILED` path is supported by engine-level failure handling rather than explicit state method semantics

### `backend/src/main/java/com/paypulse/payment/service/states/ValidatedState.java`
- [x] Exists
- [x] Allows `VALIDATED -> SENT`
- [~] `VALIDATED -> FAILED` path is supported by engine-level failure handling rather than explicit state method semantics

### `backend/src/main/java/com/paypulse/payment/service/states/SentState.java`
- [x] Exists
- [x] Allows `SENT -> COMPLETED`
- [~] `SENT -> FAILED` path is supported by engine-level failure handling rather than explicit state method semantics

### `backend/src/main/java/com/paypulse/payment/service/states/CompletedState.java`
- [x] Exists
- [x] Terminal state behavior enforced
- [x] Rejects outgoing transitions conceptually per `docs/10-UML-STATE-DIAGRAM.md`

### `backend/src/main/java/com/paypulse/payment/service/states/FailedState.java`
- [x] Exists
- [x] Terminal state behavior enforced
- [x] Rejects outgoing transitions conceptually per `docs/10-UML-STATE-DIAGRAM.md`

### `backend/src/main/java/com/paypulse/payment/service/states/PaymentStateFactory.java`
- [x] Exists
- [x] Resolves state implementation from `PaymentStatus`
- [x] Rejects null status

### `backend/src/main/java/com/paypulse/payment/service/states/InvalidStatusTransitionException.java`
- [x] Exists
- [x] Gives transition-specific error message
- [x] Supports `INVALID_STATUS_TRANSITION` error mapping

### State-machine requirement coverage
- [x] `CREATED -> VALIDATED` supported
- [x] `VALIDATED -> SENT` supported
- [x] `SENT -> COMPLETED` supported
- [x] `CREATED -> FAILED` supported at engine level
- [x] `VALIDATED -> FAILED` supported at engine level
- [x] `SENT -> FAILED` supported at engine level
- [x] `COMPLETED` is terminal
- [x] `FAILED` is terminal
- [~] State-pattern implementation matches pattern/UML docs exactly

Current assessment:
- Core state machine is implemented.
- Current implementation is functionally aligned with the lifecycle, but not an exact structural match to every UML/pattern detail (`fail()` / `TransitionResult` not modeled explicitly).

---

## 4. Transition Engine

### `backend/src/main/java/com/paypulse/payment/service/StatusTransitionEngine.java`

#### Core behavior
- [x] `getHistory(String paymentId)` implemented
- [x] `validatePayment(Payment, TriggeredBy)` implemented
- [x] `sendPayment(Payment, TriggeredBy)` implemented
- [x] `completePayment(Payment, TriggeredBy)` implemented
- [x] `markFailed(...)` implemented
- [x] Transition persistence implemented
- [x] Audit-history persistence implemented
- [x] Uses `PaymentStateFactory`
- [x] Uses `PaymentStatusHistoryRepository`
- [x] Persists `errorCode` / `errorMessage` on failure
- [x] Supports deterministic failure simulation
- [x] Supports random failure simulation
- [~] Remains the single orchestrator exactly as described in UML / architecture docs
- [~] Uses `ValidationChain` during `CREATED -> VALIDATED` exactly as described in architecture/UML/sequence docs

#### Atomicity / durability
- [~] Transition status update and audit-history write happen within transactional service methods
- [ ] Verify every transition writes the payment update and history row in the same transaction
- [ ] Verify no partial transition can persist payment status without matching history
- [ ] Verify no history row can persist without corresponding payment status update
- [ ] Verify rollback behavior when transition persistence fails mid-operation

#### Validation / failure semantics inside the engine
- [ ] `CREATED -> VALIDATED` path integrates with `ValidationChain`
- [ ] Validation success results in `VALIDATED`
- [ ] Validation failure results in `FAILED`
- [ ] Validation failure persists `errorCode` and `errorMessage`
- [ ] Validation failure writes audit row `CREATED -> FAILED`
- [ ] If validation fails, `send` is not attempted
- [ ] If validation fails, `complete` is not attempted
- [ ] If `send` fails, `complete` is not attempted
- [ ] Engine behavior matches `docs/09-UML-SEQUENCE-DIAGRAMS.md` failure-path semantics

#### Trigger-source semantics
- [~] Engine accepts `TriggeredBy`
- [ ] Automatic post-create transitions are invoked with `TriggeredBy.SYSTEM`
- [x] Manual transition endpoints can invoke engine with `TriggeredBy.CLIENT`
- [ ] Audit history semantics match `docs/11-API-DESIGN.md` examples for `CLIENT` vs `SYSTEM`

#### Resilience behavior
- [x] Send transition wrapped with resilience instance `paymentSend`
- [x] Complete transition wrapped with resilience instance `paymentComplete`
- [x] Handles breaker-open condition
- [x] Handles runtime failure condition
- [x] Deterministic failure simulation exists
- [x] Random failure simulation exists
- [~] Retry policy is functionally aligned with configured retry exception types
- [ ] Verify thrown exception types actually trigger retry behavior from config
- [ ] Verify failure burst can open the breaker
- [ ] Verify half-open recovery path works
- [ ] Verify closed-state recovery after failures subside

#### Event / observer integration
- [ ] Transition event is published on every successful state change if observer/event pattern is in scope
- [ ] Event publication covers `VALIDATED`, `SENT`, `COMPLETED`, and `FAILED`
- [ ] Event publication does not replace DB history as source of truth
- [ ] Analytics/event consumers can observe transition changes without tight coupling back into the engine
- [ ] If event publication is intentionally omitted, deviation from `docs/06-DESIGN-PATTERNS.md` is documented

#### Architecture / UML conformance
- [ ] `validate` step uses business-rule validation path described in `docs/05-ARCHITECTURE.md`
- [ ] Engine integration matches `docs/08-UML-CLASS-DIAGRAM.md` service responsibilities closely enough
- [ ] Engine remains business-layer code, not controller-layer code
- [ ] API layer does not contain transition logic that belongs here

#### Remaining fixes / verification
- [ ] Verify retry policy is actually triggered by thrown exception types
- [ ] Confirm returned API shape is DTO-based, not raw entity-based
- [ ] Confirm M3 auto-flow uses `TriggeredBy.SYSTEM`
- [ ] Confirm history rows are written correctly for every successful and failed transition
- [ ] Confirm concurrency conflict behavior matches API contract

Current assessment:
- Substantially implemented.
- Main remaining work is hardening, validation-path integration, transaction guarantees, event/observer coverage, and final contract alignment.

---

## 5. Repository

### `backend/src/main/java/com/paypulse/payment/repository/PaymentStatusHistoryRepository.java`
- [x] Repository exists
- [x] Extends `JpaRepository`
- [x] Supports ordered history lookup by payment id
- [x] Supports audit retrieval use case from `docs/04-SRS.md` FR-6.1
- [x] Supports read-path sequence in `docs/09-UML-SEQUENCE-DIAGRAMS.md`

#### Repository behavior expectations
- [x] Retrieval is ordered by `occurredAt`
- [x] Retrieval is scoped to a single payment id
- [ ] Append-only behavior is verified by tests
- [ ] Repository behavior is exercised through integration history scenarios

Current assessment:
- Implemented.

---

## 6. PaymentController (M2-owned methods only)

### `backend/src/main/java/com/paypulse/payment/api/PaymentController.java`

#### M2 endpoint ownership
- [x] `GET /payments/{id}/history` implemented
- [x] `POST /payments/{id}/validate` implemented
- [x] `POST /payments/{id}/send` implemented
- [x] `POST /payments/{id}/complete` implemented

#### Supporting behavior
- [x] Looks up payment by id
- [x] Returns 404 when payment is missing
- [x] Handles invalid transition errors
- [x] Handles concurrency/conflict errors
- [~] Shared-file protocol from `docs/13-WORK-DISTRIBUTION.md` is respected cleanly
- [~] Controller delegates according to strict architecture/UML layering

#### API boundary / response contract
- [ ] Controller returns DTOs only, never JPA entities
- [ ] `GET /payments/{id}/history` returns ordered `PaymentHistoryResponse[]`
- [ ] `POST /payments/{id}/validate|send|complete` return `PaymentResponse`
- [ ] Explicit transition endpoint may return `200` with payment `status=FAILED` when the transition operation succeeds but business processing fails
- [x] `404 PAYMENT_NOT_FOUND` behavior exists
- [x] `400 INVALID_STATUS_TRANSITION` behavior exists
- [x] `409 PROCESSING_ERROR` behavior exists for concurrency/conflict
- [ ] Error responses align exactly with shared `ApiError` contract from `docs/11-API-DESIGN.md`
- [ ] Controller does not leak entity-only/internal fields across the API boundary

#### Rate-limit / cross-cutting behavior expectations
- [ ] M2 endpoints conform to shared rate-limit behavior
- [ ] `429 RATE_LIMIT_EXCEEDED` is verified for M2 endpoints
- [ ] `Retry-After` header behavior is verified for rejected M2 requests
- [ ] `X-RateLimit-*` headers are present per shared contract if globally applied

#### Architecture / UML conformance
- [ ] `PaymentController` delegates to `PaymentService` for M2-owned methods if strict UML/architecture conformance is required
- [ ] Controller does not use `PaymentRepository` directly unless deviation is documented
- [ ] Trigger endpoints align with service-layer methods from `docs/08-UML-CLASS-DIAGRAM.md` (`triggerValidate`, `triggerSend`, `triggerComplete`)
- [ ] `getHistory()` aligns with service-layer `getHistory()` path from UML/sequence docs
- [ ] API layer contains no business logic beyond delegation/error translation

#### Remaining fixes / integration
- [ ] Confirm response type matches shared DTO strategy from M3
- [ ] Confirm history endpoint returns mapped history DTOs if required by API contract
- [ ] Confirm local exception handlers do not conflict with M3 `GlobalExceptionHandler`
- [ ] Re-check after M3/M4 merge their methods into shared `PaymentController`
- [ ] Re-check after OpenAPI/API contract validation is performed against actual responses

Current assessment:
- M2-owned controller methods are implemented.
- Shared-file integration and strict API-layer contract alignment are not fully complete yet.

---

## 7. Resilience Configuration

### `backend/src/main/java/com/paypulse/common/resilience/ResilienceConfig.java`
- [x] Configuration class exists
- [x] Looks up circuit breaker by instance name
- [x] Looks up retry by instance name
- [x] Applies retry + circuit breaker wrapper
- [x] Provides simulation `Random` bean

#### Configuration expectations from design docs
- [x] Supports named instances `paymentSend` and `paymentComplete`
- [x] Supports circuit breaker + retry wrapping for simulated processing
- [ ] Retry configuration behavior is validated against actual exception types thrown by engine
- [ ] Breaker-open behavior is proven by tests
- [ ] Half-open probing / recovery behavior is proven by tests
- [ ] Closed-state recovery after transient failure burst is proven by tests
- [ ] Configuration is shown to satisfy resilience expectations from `docs/05-ARCHITECTURE.md`, `docs/06-DESIGN-PATTERNS.md`, and `docs/07-TESTING-STRATEGY.md`

Remaining work:
- [ ] Verify config behavior with real exception types thrown by transition engine
- [ ] Verify open/half-open/closed breaker lifecycle through tests

Current assessment:
- Implemented structurally.
- Still needs behavior verification.

---

## 8. Frontend Deliverable

### `frontend/src/components/StatusHistoryTimeline.tsx`
- [ ] Component exists
- [ ] Accepts props `{ history: PaymentHistoryResponse[] }`
- [ ] Pure/presentational only
- [ ] Does not fetch data itself
- [ ] Renders ordered status timeline
- [ ] Matches `chirag/04-wireframes/payment-details.html`
- [ ] Ready for M4 `PaymentDetailsPage.tsx` integration
- [ ] Displays transition ordering clearly
- [ ] Displays `previousStatus -> newStatus` clearly
- [ ] Displays `errorCode` / `errorMessage` for failure entries
- [ ] Displays `triggeredBy`
- [ ] Displays timestamps in a UI-appropriate format
- [ ] Handles empty history gracefully
- [ ] Handles failed-payment history gracefully
- [ ] Assumes parent passes already-ordered history list

Current assessment:
- Not implemented.

---

## 9. Tests Owned by M2

### `backend/src/test/java/com/paypulse/payment/service/StatusTransitionEngineTest.java`
- [x] Basic engine test file exists
- [x] Covers `CREATED -> VALIDATED`
- [x] Covers invalid send from `CREATED`
- [x] Covers deterministic failure on send
- [x] Covers `SENT -> COMPLETED`

#### Required unit-transition coverage from `docs/07-TESTING-STRATEGY.md`
- [ ] `CreatedState`: validate succeeds
- [ ] `CreatedState`: send rejected
- [ ] `CreatedState`: complete rejected
- [ ] `ValidatedState`: send succeeds
- [ ] `ValidatedState`: validate rejected
- [ ] `ValidatedState`: complete rejected
- [ ] `SentState`: complete succeeds
- [ ] `SentState`: validate rejected
- [ ] `SentState`: send rejected
- [ ] `CompletedState`: validate rejected
- [ ] `CompletedState`: send rejected
- [ ] `CompletedState`: complete rejected
- [ ] `FailedState`: validate rejected
- [ ] `FailedState`: send rejected
- [ ] `FailedState`: complete rejected

#### Required engine-level behavior coverage
- [ ] Validation failure transitions `CREATED -> FAILED`
- [ ] Send failure transitions `VALIDATED -> FAILED`
- [ ] Complete failure transitions `SENT -> FAILED`
- [ ] History row assertions for success cases
- [ ] History row assertions for failure cases
- [ ] `previousStatus` assertions
- [ ] `newStatus` assertions
- [ ] `triggeredBy` assertions
- [ ] `errorCode` assertions
- [ ] `errorMessage` assertions
- [ ] Circuit breaker open behavior assertions
- [ ] Retry behavior assertions
- [ ] Concurrency/conflict scenario assertions
- [ ] Short-circuit behavior after failure assertions (`complete` not attempted after failed `send`, etc.)

Current assessment:
- Partially implemented.

---

### `backend/src/test/java/com/paypulse/payment/repository/PaymentStatusHistoryRepositoryTest.java`
- [x] Test file exists
- [x] Verifies history filtering by payment id
- [x] Verifies ascending order by `occurredAt`

Remaining coverage:
- [ ] Empty-history case
- [ ] Additional assertions for `previousStatus`
- [ ] Additional assertions for `newStatus`
- [ ] Additional assertions for `triggeredBy`
- [ ] Additional assertions for stored error metadata
- [ ] Append-only usage assumptions are validated well enough for audit-trail confidence

Current assessment:
- Implemented, but can be strengthened.

---

### `backend/src/test/java/com/paypulse/payment/api/PaymentControllerTest.java`
- [x] Test file exists
- [x] Covers 404 for missing payment
- [x] Covers validate success
- [x] Covers invalid complete transition -> 400

Remaining coverage:
- [ ] History success response
- [ ] Send success response
- [ ] Complete success response
- [ ] Explicit transition returning `200` with `status=FAILED`
- [ ] Conflict/concurrency response
- [ ] Error response body consistency
- [ ] DTO response shape assertions
- [ ] Any API-contract-specific assertions for M2 endpoints
- [ ] `GET /history` ordered response semantics
- [ ] Rate-limit response behavior if testable at this layer

Current assessment:
- Partially implemented.

---

### Circuit breaker / resilience test
- [ ] Dedicated circuit breaker open/close test exists
- [ ] Breaker opens under repeated simulated failures
- [ ] Calls are short-circuited while breaker is open
- [ ] Breaker transitions to half-open after wait duration
- [ ] Breaker closes again after successful recovery

Current assessment:
- Not implemented.

---

### Integration / E2E coverage expected of M2-relevant behavior
- [ ] Happy-path create flow reaches `COMPLETED`
- [ ] Happy-path history contains `null -> CREATED -> VALIDATED -> SENT -> COMPLETED`
- [ ] Invalid state transition endpoint returns `400 INVALID_STATUS_TRANSITION`
- [ ] Concurrent transition requests result in one success + one conflict, with consistent DB state
- [ ] Failure path from validation produces `FAILED` and history entry
- [ ] Failure path from send produces `FAILED` and history entry
- [ ] Failure path from complete produces `FAILED` and history entry
- [ ] Transition + history atomicity is proven in integration tests

Current assessment:
- Not fully covered from current visible tests.

---

### Load / resilience validation
#### `load-tests/payments-burst.js`
- [x] Load-test script exists
- [~] Covers overall payment creation burst load
- [ ] Specifically validates M2 send/complete resilience flows
- [ ] Approaches the ~667 req/sec / 40k req/min target from original docs
- [ ] Verifies intentional excess load yields `429`, not 5xx/timeouts
- [ ] Verifies breaker-open behavior under simulated failure burst
- [ ] Verifies system recovery after failure burst subsides

Current assessment:
- Exists, but only partially aligned to M2-specific resilience scope.

---

## 10. M2 Integration Points (only where M2 must coordinate)

### With M1
- [x] M2 uses M1 `Payment` entity
- [x] M2 uses M1 `PaymentRepository`
- [x] M2 relies on M1 optimistic locking field (`version`)
- [x] M2 relies on M1 account/payment persistence foundation

No action here unless integration breaks.

---

### With M3
- [ ] Confirm M3 `PaymentService.createPayment()` calls M2 transition engine for automatic lifecycle progression
- [ ] Confirm M3 invokes transitions in correct order: validate -> send -> complete
- [ ] Confirm M3 short-circuits progression when validation fails
- [ ] Confirm M3 short-circuits progression when send fails
- [ ] Confirm M3 uses `TriggeredBy.SYSTEM` for automatic transitions
- [ ] Confirm M3 creates initial `null -> CREATED` history row before M2-owned auto-transitions
- [ ] Confirm M3 DTO/mapping layer is used for M2 endpoint responses where required
- [ ] Confirm M3 `PaymentMapper.toHistoryResponse(...)` is used where required
- [ ] Confirm M3 global error handling strategy does not conflict with M2 local handlers
- [ ] Confirm M2 transition outcomes appear correctly in `PaymentResponse` returned from create flow
- [ ] Confirm synchronous post-create validation assumption from `docs/04-SRS.md` FR-2.4 is respected

Current assessment:
- Integration required; not fully verifiable from current code snapshot.

---

### With M4
- [ ] Deliver `StatusHistoryTimeline` with frozen prop contract
- [ ] Confirm payload shape expected by `PaymentDetailsPage.tsx`
- [ ] Confirm history ordering and display match frontend needs
- [ ] Confirm failure entries render `errorCode` / `errorMessage` correctly
- [ ] Confirm terminal-state timeline rendering is clear for `COMPLETED` and `FAILED`

Current assessment:
- Not complete until frontend component exists.

---

## 11. Cleanup / Structural Issues

### Placeholder / misplaced file
#### `backend/src/main/java/com/paypulse/payment/domain/PaymentStatusHistoryRepository.java`
- [~] Placeholder file exists
- [ ] Remove or formally document why it remains

### Package consistency
- [~] `PaymentStatus` package/location matches work-distribution / architecture / UML docs exactly

### UML / architecture drift to document or resolve
- [~] `PaymentController` currently matches strict service-layer dependency shape from UML
- [~] `StatusTransitionEngine` currently matches UML/pattern semantics exactly (`ValidationChain`, `TransitionResult`, `fail()` pattern details)
- [~] Event/observer publication from transition engine is implemented or deviation is documented
- [~] API layer currently respects DTO-only boundary fully

Current assessment:
- Non-blocking, but should be cleaned up or documented.

---

## 12. Overall M2 Completion Summary

### Implemented
- [x] Payment status enum
- [x] Payment status history entity
- [x] Payment history repository
- [x] State interface and concrete state classes
- [x] State factory
- [x] Invalid transition exception
- [x] Transition engine core
- [x] Resilience wrapper integration
- [x] M2 controller endpoints (basic form)
- [x] Basic repository/service/controller tests
- [x] Initial load-test script exists

### Implemented but still needs verification / alignment
- [~] Transactional atomicity of status + history persistence
- [~] Failure semantics across all stages
- [~] Concurrency conflict handling alignment with API contract
- [~] Trigger source (`CLIENT` / `SYSTEM`) semantics
- [~] Resilience behavior against real retry/breaker conditions
- [~] Architecture/UML alignment for controller/service boundaries
- [~] DTO-only API boundary alignment
- [~] Shared-file integration with M3/M4

### Not implemented / incomplete
- [ ] `frontend/src/components/StatusHistoryTimeline.tsx`
- [ ] Full transition test matrix
- [ ] Full engine-level failure-path coverage
- [ ] Dedicated circuit breaker open/close test
- [ ] Full controller endpoint coverage
- [ ] Retry semantics verification
- [ ] ValidationChain-based validation-step verification
- [ ] Event / observer integration verification
- [ ] Final DTO/API-contract alignment with M3
- [ ] Final automatic lifecycle integration verification with M3
- [ ] Full load/resilience verification at documented target behavior
- [ ] Cleanup of structural inconsistencies

---

## 13. Simple Completion Rate

Approximate M2 completion based on current repository state and original document requirements:

- Backend core implementation: high
- State-machine correctness: medium-high
- Test completeness: medium
- Frontend deliverable: low
- Architecture/UML conformance: medium-low
- Cross-member integration completeness: medium-low
- Resilience verification depth: low-medium

Estimated current M2 completion: **65%–75%**

This estimate should be revised after:
1. `StatusHistoryTimeline.tsx` is added
2. resilience tests are completed
3. M3 integration is verified
4. DTO/API alignment is finalized
5. validation-step behavior is confirmed against the original docs
6. architecture/UML drift is either resolved or explicitly documented

---

## 14. Requirement Traceability (M2-relevant only)

### Functional Requirements from `docs/04-SRS.md`
- [ ] FR-2.1 Validation rules run against `CREATED`
- [ ] FR-2.2 Validation success -> `VALIDATED` + history
- [ ] FR-2.3 Validation failure -> `FAILED` + error details + history
- [ ] FR-2.4 Validation triggered synchronously after creation
- [x] FR-3.1 Send success -> `SENT`
- [x] FR-3.2 Send failure can result in `FAILED`
- [x] FR-4.1 Complete success -> `COMPLETED`
- [x] FR-4.2 Complete failure can result in `FAILED`
- [x] FR-6.1 Full ordered history retrieval exists in repository/service/controller path
- [x] FR-8.1 Failed payment model supports `errorCode` + `errorMessage`
- [x] FR-10 Transition table enforced for legal/illegal transitions
- [~] Explicit transition endpoints behave like corresponding automatic lifecycle steps

### Non-Functional Requirements from `docs/04-SRS.md`
- [~] NFR-2 Append-only audit history preserved
- [x] NFR-3 Invalid transitions rejected server-side
- [~] NFR-4 Swagger/OpenAPI stays aligned with actual M2 endpoints/responses
- [~] NFR-5 State logic is unit-testable independent of Spring/DB
- [~] NFR-6 Error contract alignment with shared `ApiError` shape
- [~] NFR-8 Concurrent transitions do not corrupt payment state
- [~] NFR-12 Resilience degrades gracefully under transient failure bursts
- [~] NFR-13 Status update + audit insert are atomic

Current assessment:
- Functional core is mostly present.
- Full requirement traceability still depends on validation integration, DTO/API conformance, test expansion, and resilience verification.

---

## 15. Acceptance Criteria Coverage (M2-relevant only)

Derived from `docs/04-SRS.md` §10 and supporting docs.

- [ ] Newly created payment automatically progresses to `VALIDATED` or `FAILED` without manual client intervention
- [x] A `VALIDATED` payment can be progressed to `SENT`
- [x] A `SENT` payment can be progressed to `COMPLETED`
- [x] A payment can fail during send or complete, with failure captured
- [ ] Validation-stage failure path is verified end-to-end
- [~] `GET /payments/{id}/history` returns full ordered audit trail in final contract shape
- [x] Illegal transition attempts are rejected with `INVALID_STATUS_TRANSITION`
- [~] M2 endpoints are reflected correctly in Swagger/OpenAPI
- [ ] M2 endpoints are verified under rate-limit behavior where relevant
- [ ] Transition history clearly distinguishes manual vs automatic trigger source

Current assessment:
- Acceptance-criteria coverage is partial and should not yet be considered complete.

---

## 16. Final Review Notes for M2

Before M2 can be considered fully complete, verify all of the following:

- [ ] The lifecycle works functionally
- [ ] The lifecycle matches the documented state machine
- [ ] The lifecycle matches the documented API contract
- [ ] The lifecycle matches the documented sequence/architecture expectations closely enough
- [ ] Audit rows are append-only and atomically persisted
- [ ] Validation-stage behavior is fully wired and tested
- [ ] Resilience behavior is not just configured, but proven
- [ ] Transition endpoints return DTOs, not entities
- [ ] Frontend timeline component is delivered and consumable by M4
- [ ] Cross-member integration with M3 is verified end-to-end
- [ ] Any remaining architecture/UML drift is explicitly documented if not fixed