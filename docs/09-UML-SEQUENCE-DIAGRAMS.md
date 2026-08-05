# 🔁 UML-SEQUENCE-DIAGRAMS.md — Sequence Diagrams

Related: `08-UML-CLASS-DIAGRAM.md`, `05-ARCHITECTURE.md` §3 (request flow narrative). Rendered in Mermaid.

> **Updated 31 Jul 2026** post-customer-meeting: diagram 1 updated for account selection; new diagrams 8 (Rate Limit Exceeded) and 9 (KPI Dashboard fetch) added — MEM-017/019/020.

## 1. Create Payment — Happy Path (auto-progresses to COMPLETED, now with Account selection)

```mermaid
sequenceDiagram
    actor Client
    participant Filter as RateLimitFilter
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Idem as IdempotencyService
    participant AcctSvc as AccountService
    participant Repo as PaymentRepository
    participant Engine as StatusTransitionEngine
    participant HistRepo as PaymentStatusHistoryRepository
    participant Mapper as PaymentMapper

    Client->>Filter: POST /payments (body incl. sourceAccountId, Idempotency-Key)
    Filter->>Filter: check token bucket (has capacity)
    Filter->>Ctrl: forward request
    Ctrl->>Ctrl: @Valid bean validation (field-level)
    Ctrl->>Svc: createPayment(request)
    Svc->>Idem: findExisting(idempotencyKey)
    Idem->>Repo: findByIdempotencyKey(key)
    Repo-->>Idem: empty
    Idem-->>Svc: empty
    Svc->>AcctSvc: getActiveAccount(sourceAccountId)
    AcctSvc-->>Svc: Account(currency=INR, status=ACTIVE)
    Svc->>Mapper: toEntity(request)
    Mapper-->>Svc: Payment(status=CREATED, sourceAccountId=...)
    Svc->>Repo: save(payment)
    Repo-->>Svc: payment (id assigned)
    Svc->>HistRepo: save(history: null→CREATED)
    Svc->>Engine: validate(payment)
    Engine->>Engine: run ValidationChain (amount/currency-matches-account/account-active)
    Engine->>Repo: save(payment status=VALIDATED)
    Engine->>HistRepo: save(history: CREATED→VALIDATED)
    Engine-->>Svc: payment(VALIDATED)
    Svc->>Engine: send(payment)
    Engine->>Engine: simulate transmission (Circuit Breaker + Retry wrapped)
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

## 8. Rate Limit Exceeded (new, MEM-020)

```mermaid
sequenceDiagram
    actor Client
    participant Filter as RateLimitFilter
    participant Bucket as TokenBucket (Bucket4j)
    participant Ctrl as PaymentController

    Client->>Filter: POST /payments (client already at limit)
    Filter->>Bucket: tryConsume(1 token)
    Bucket-->>Filter: false (no tokens available)
    Note over Filter: Short-circuit — request never reaches the controller/service/DB
    Filter-->>Client: 429 Too Many Requests + ApiError(RATE_LIMIT_EXCEEDED) + Retry-After header
```

> This is deliberately the **cheapest possible rejection point** in the whole architecture (§4 of `05-ARCHITECTURE.md`) — no DB connection, no service logic, no business validation is spent on a request that's going to be rejected anyway.

## 9. KPI Dashboard Fetch (new, MEM-019)

```mermaid
sequenceDiagram
    actor Client as React App (landing page)
    participant Ctrl as AnalyticsController
    participant Svc as AnalyticsService
    participant Repo as PaymentRepository
    participant HistRepo as PaymentStatusHistoryRepository

    Client->>Ctrl: GET /analytics/summary
    Ctrl->>Svc: computeSummary(from, to)
    Svc->>Repo: aggregate counts by status, volume by currency
    Repo-->>Svc: raw aggregates
    Svc->>HistRepo: aggregate avg(occurred_at[terminal] - occurred_at[CREATED])
    HistRepo-->>Svc: avg processing time
    Svc->>Svc: compute successRatePct, failureRatePct, throughputPerMinute, topFailureReasons
    Svc-->>Ctrl: KpiSummaryResponse
    Ctrl-->>Client: 200 OK + KpiSummaryResponse
    Note over Client: Rendered as the FIRST screen the user sees (customer directive)
```

---

## V2 Sequence Diagrams (05 Aug 2026 — MEM-023–034)

## 10. Notification Dispatch on Payment Completion (new, MEM-025/026)

```mermaid
sequenceDiagram
    participant Engine as StatusTransitionEngine
    participant Bus as ApplicationEventPublisher
    participant Listener as PaymentNotificationListener
    participant AcctSvc as AccountService
    participant NotifSvc as NotificationService
    participant EmailSvc as EmailService
    participant Log as NotificationLogRepository

    Engine->>Bus: publish(PaymentStatusChangedEvent: →COMPLETED)
    Note over Bus,Listener: @TransactionalEventListener(AFTER_COMMIT) — payment row already durably saved
    Bus->>Listener: onPaymentStatusChanged(event)
    Listener->>AcctSvc: getActiveAccount(payment.sourceAccountId)
    AcctSvc-->>Listener: Account(ownerEmail, ownerName)
    alt ownerEmail present
        Listener->>NotifSvc: notifyPaymentCompleted(email, name, paymentId, vars)
        NotifSvc->>EmailSvc: sendAsync(EmailRequest)
        EmailSvc->>Log: save(NotificationLog status=SENT/FAILED)
        EmailSvc-->>NotifSvc: EmailResult
    else no email configured
        Listener->>Listener: log warning, skip send
    end
    Note over Listener: Runs on notification-executor thread — never blocks the original request thread
```

## 11. Payment Cancellation (new, feature #18, MEM-029)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant Svc as PaymentService
    participant Repo as PaymentRepository
    participant Engine as StatusTransitionEngine
    participant State as CreatedState/CancelledState
    participant HistRepo as PaymentStatusHistoryRepository

    Client->>Ctrl: POST /payments/{id}/cancel
    Ctrl->>Svc: cancelPayment(id)
    Svc->>Repo: findById(id)
    Repo-->>Svc: payment
    alt status == CREATED
        Svc->>Engine: cancel(payment)
        Engine->>State: transition to CANCELLED
        Engine->>Repo: save(payment status=CANCELLED)
        Engine->>HistRepo: save(history: CREATED→CANCELLED, triggeredBy=CLIENT)
        Engine-->>Svc: payment(CANCELLED)
        Svc-->>Ctrl: payment(CANCELLED)
        Ctrl-->>Client: 200 OK + PaymentResponse(status=CANCELLED)
    else status != CREATED
        Svc-->>Ctrl: throws PaymentNotCancellableException
        Ctrl-->>Client: 409 Conflict + ApiError(PAYMENT_NOT_CANCELLABLE)
    end
```

## 12. Payment Reversal (new, feature #19, MEM-030)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant RevSvc as ReversalService
    participant Repo as PaymentRepository
    participant Svc as PaymentService

    Client->>Ctrl: POST /payments/{id}/reverse
    Ctrl->>RevSvc: reverse(id)
    RevSvc->>Repo: findById(id)
    Repo-->>RevSvc: original payment(status=COMPLETED, reversed=false)
    alt eligible (COMPLETED and not already reversed)
        RevSvc->>Svc: createPayment(swapped source/destination, reversalOfPaymentId=original.id)
        Note over Svc: Normal create-payment pipeline — idempotency, validation, auto-progression, audit trail
        Svc-->>RevSvc: newPayment (own lifecycle/status)
        RevSvc->>Repo: save(original: reversed=true, reversalPaymentId=newPayment.id)
        Note over Repo: original.status is NEVER changed — remains COMPLETED (audit integrity, NFR-2)
        RevSvc-->>Ctrl: newPayment
        Ctrl-->>Client: 201 Created + PaymentResponse(new reversal payment)
    else already reversed
        RevSvc-->>Ctrl: throws PaymentAlreadyReversedException
        Ctrl-->>Client: 409 Conflict + ApiError(PAYMENT_ALREADY_REVERSED)
    else not COMPLETED
        RevSvc-->>Ctrl: throws InvalidStatusTransitionException
        Ctrl-->>Client: 409 Conflict + ApiError(INVALID_STATUS_TRANSITION)
    end
```

## 13. Dashboard Live Update via SSE (new, MEM-027)

```mermaid
sequenceDiagram
    actor Client as Browser (EventSource)
    participant Ctrl as AnalyticsController
    participant Stream as DashboardStreamService
    participant Bus as ApplicationEventPublisher
    participant Svc as AnalyticsService

    Client->>Ctrl: GET /analytics/stream (EventSource connect)
    Ctrl->>Stream: subscribe()
    Stream-->>Client: SseEmitter registered (connection stays open)

    Note over Bus,Stream: Elsewhere: a payment transitions
    Bus->>Stream: onPaymentStatusChanged(event)
    Stream->>Stream: schedule debounced recompute (coalesce bursts, ≤1 push/2s)
    Stream->>Svc: computeSummary(from, to)
    Svc-->>Stream: KpiSummaryResponse
    Stream-->>Client: SSE event: data: {KpiSummaryResponse JSON}
    Note over Client: Dashboard updates in place — no polling, no page reload
```

## 14. CSV Export (new, feature #14, MEM-032)

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as PaymentController
    participant ExportSvc as PaymentCsvExportService
    participant Repo as PaymentRepository

    Client->>Ctrl: GET /payments/export?status=FAILED&sort=createdAt,desc
    Ctrl->>ExportSvc: streamExport(filters, response.outputStream)
    ExportSvc->>Repo: countByFilters(filters)
    Repo-->>ExportSvc: rowCount
    alt rowCount > max-rows
        ExportSvc-->>Ctrl: throws ExportTooLargeException
        Ctrl-->>Client: 400 Bad Request + ApiError(EXPORT_TOO_LARGE)
    else within cap
        ExportSvc->>Repo: streamByFilters(filters)  (Stream~Payment~, DB cursor)
        loop each row (streamed, not buffered)
            Repo-->>ExportSvc: Payment
            ExportSvc->>Client: write CSV row directly to response OutputStream
        end
        Ctrl-->>Client: 200 OK, Content-Type: text/csv (streamed)
    end
```

