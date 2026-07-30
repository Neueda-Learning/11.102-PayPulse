# 🔁 UML-SEQUENCE-DIAGRAMS.md — Sequence Diagrams

Related: `08-UML-CLASS-DIAGRAM.md`, `05-ARCHITECTURE.md` §3 (request flow narrative). Rendered in Mermaid.

## 1. Create Payment — Happy Path (auto-progresses to COMPLETED)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Idem as IdempotencyService
    participant Repo as PaymentRepository
    participant Engine as StatusTransitionEngine
    participant HistRepo as PaymentStatusHistoryRepository
    participant Mapper as PaymentMapper

    Client->>Ctrl: POST /payments (body, Idempotency-Key)
    Ctrl->>Ctrl: @Valid bean validation (field-level)
    Ctrl->>Svc: createPayment(request)
    Svc->>Idem: findExisting(idempotencyKey)
    Idem->>Repo: findByIdempotencyKey(key)
    Repo-->>Idem: empty
    Idem-->>Svc: empty
    Svc->>Mapper: toEntity(request)
    Mapper-->>Svc: Payment(status=CREATED)
    Svc->>Repo: save(payment)
    Repo-->>Svc: payment (id assigned)
    Svc->>HistRepo: save(history: null→CREATED)
    Svc->>Engine: validate(payment)
    Engine->>Engine: run ValidationChain (amount/currency/account)
    Engine->>Repo: save(payment status=VALIDATED)
    Engine->>HistRepo: save(history: CREATED→VALIDATED)
    Engine-->>Svc: payment(VALIDATED)
    Svc->>Engine: send(payment)
    Engine->>Engine: simulate transmission
    Engine->>Repo: save(payment status=SENT)
    Engine->>HistRepo: save(history: VALIDATED→SENT)
    Engine-->>Svc: payment(SENT)
    Svc->>Engine: complete(payment)
    Engine->>Engine: simulate confirmation
    Engine->>Repo: save(payment status=COMPLETED)
    Engine->>HistRepo: save(history: SENT→COMPLETED)
    Engine-->>Svc: payment(COMPLETED)
    Svc-->>Ctrl: payment(COMPLETED)
    Ctrl->>Mapper: toResponse(payment)
    Mapper-->>Ctrl: PaymentResponse
    Ctrl-->>Client: 201 Created + PaymentResponse
```

## 2. Create Payment — Duplicate Idempotency Key (MEM-006)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Idem as IdempotencyService
    participant Repo as PaymentRepository
    participant Mapper as PaymentMapper

    Client->>Ctrl: POST /payments (same Idempotency-Key as before)
    Ctrl->>Svc: createPayment(request)
    Svc->>Idem: findExisting(idempotencyKey)
    Idem->>Repo: findByIdempotencyKey(key)
    Repo-->>Idem: existingPayment
    Idem-->>Svc: existingPayment
    Note over Svc: Short-circuit — do NOT create a new payment
    Svc-->>Ctrl: existingPayment
    Ctrl->>Mapper: toResponse(existingPayment)
    Mapper-->>Ctrl: PaymentResponse
    Ctrl-->>Client: 200 OK + existing PaymentResponse
```

## 3. Failure Path — Validation Fails at CREATED→VALIDATED

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Repo as PaymentRepository
    participant Engine as StatusTransitionEngine
    participant Chain as ValidationChain
    participant HistRepo as PaymentStatusHistoryRepository

    Client->>Ctrl: POST /payments (unsupported currency, e.g. "XYZ")
    Ctrl->>Svc: createPayment(request)
    Svc->>Repo: save(payment status=CREATED)
    Svc->>HistRepo: save(history: null→CREATED)
    Svc->>Engine: validate(payment)
    Engine->>Chain: runAll(payment)
    Chain-->>Engine: [ValidationError(INVALID_CURRENCY)]
    Engine->>Repo: save(payment status=FAILED, errorCode=INVALID_CURRENCY)
    Engine->>HistRepo: save(history: CREATED→FAILED, errorCode=INVALID_CURRENCY)
    Engine-->>Svc: payment(FAILED)
    Svc-->>Ctrl: payment(FAILED)
    Ctrl-->>Client: 201 Created + PaymentResponse(status=FAILED, errorCode=INVALID_CURRENCY)
```

> Note: creation itself still returns `201` (the *resource* was created successfully) — it's the payment's *status* that reflects `FAILED`. Client inspects `status`/`errorCode` in the response body, consistent with FR-1.4 Option (b) and NFR-6 (consistent error contract for business-rule failures, as opposed to `400` for pure request-shape validation).

## 4. Failure Path — Simulated Network Error at SENT stage

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Engine as StatusTransitionEngine
    participant Repo as PaymentRepository
    participant HistRepo as PaymentStatusHistoryRepository

    Client->>Ctrl: POST /payments/{id}/send
    Ctrl->>Svc: triggerSend(id)
    Svc->>Repo: findById(id)
    Repo-->>Svc: payment(status=VALIDATED)
    Svc->>Engine: send(payment)
    Engine->>Engine: simulate transmission (injected failure)
    Engine->>Repo: save(payment status=FAILED, errorCode=NETWORK_ERROR)
    Engine->>HistRepo: save(history: VALIDATED→FAILED, errorCode=NETWORK_ERROR)
    Engine-->>Svc: payment(FAILED)
    Svc-->>Ctrl: payment(FAILED)
    Ctrl-->>Client: 200 OK + PaymentResponse(status=FAILED, errorCode=NETWORK_ERROR)
```

## 5. Illegal Transition Attempt (Rejected)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Repo as PaymentRepository
    participant Engine as StatusTransitionEngine
    participant State as CompletedState

    Client->>Ctrl: POST /payments/{id}/validate  (payment is already COMPLETED)
    Ctrl->>Svc: triggerValidate(id)
    Svc->>Repo: findById(id)
    Repo-->>Svc: payment(status=COMPLETED)
    Svc->>Engine: validate(payment)
    Engine->>State: validate(payment, chain)
    State-->>Engine: TransitionResult(success=false, errorCode=INVALID_STATUS_TRANSITION)
    Engine-->>Svc: throws InvalidStatusTransitionException
    Svc-->>Ctrl: propagates exception
    Ctrl-->>Client: 400 Bad Request + ApiError(errorCode=INVALID_STATUS_TRANSITION)
```

## 6. Get Payment Details + History (Read Path)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Repo as PaymentRepository
    participant HistRepo as PaymentStatusHistoryRepository
    participant Mapper as PaymentMapper

    Client->>Ctrl: GET /payments/{id}
    Ctrl->>Svc: getById(id)
    Svc->>Repo: findById(id)
    Repo-->>Svc: payment (or empty)
    alt not found
        Svc-->>Ctrl: throws PaymentNotFoundException
        Ctrl-->>Client: 404 Not Found + ApiError(PAYMENT_NOT_FOUND)
    else found
        Svc-->>Ctrl: payment
        Ctrl->>Mapper: toResponse(payment)
        Mapper-->>Ctrl: PaymentResponse
        Ctrl-->>Client: 200 OK + PaymentResponse
    end

    Client->>Ctrl: GET /payments/{id}/history
    Ctrl->>Svc: getHistory(id)
    Svc->>Repo: existsById(id)
    Repo-->>Svc: true
    Svc->>HistRepo: findByPaymentIdOrderByOccurredAtAsc(id)
    HistRepo-->>Svc: List~PaymentStatusHistory~
    Svc-->>Ctrl: history list
    Ctrl->>Mapper: toHistoryResponse(each)
    Mapper-->>Ctrl: List~PaymentHistoryResponse~
    Ctrl-->>Client: 200 OK + [history...]
```

## 7. List/Search/Filter Payments

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Repo as PaymentRepository
    participant Mapper as PaymentMapper

    Client->>Ctrl: GET /payments?status=FAILED&page=0&size=20
    Ctrl->>Svc: list(status=FAILED, pageable)
    Svc->>Repo: findByStatus(FAILED, pageable)
    Repo-->>Svc: Page~Payment~
    Svc-->>Ctrl: Page~Payment~
    Ctrl->>Mapper: toResponse(each)
    Mapper-->>Ctrl: Page~PaymentResponse~
    Ctrl-->>Client: 200 OK + paginated list
```

