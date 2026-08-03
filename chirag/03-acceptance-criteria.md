# 3. Acceptance Criteria - PayPulse MVP

Each Must Have feature from `01-feature-list.md` has clear acceptance criteria below, so development and QA share the same definition of "done."

---

## Feature: KPI Dashboard (Landing Page)

Acceptance Criteria
- Dashboard is the first screen shown when the app loads (default route).
- Dashboard displays: total payments, success rate %, failure rate %, average processing time, throughput per minute.
- Dashboard displays transaction volume broken out separately for INR and USD (never combined/converted).
- Dashboard displays the top failure reasons (error codes) with counts.
- All figures are computed from real payment data, not hardcoded placeholder values.
- No metric is shown that doesn't answer a real business question (no vanity metrics).

## Feature: Multi-Account Support

Acceptance Criteria
- User can view a list of their own accounts, each showing: label, account number, currency, and status (Active/Inactive).
- Only ACTIVE accounts can be selected as a payment source.
- Selecting an INACTIVE account as source is rejected with a clear error before the payment is submitted.
- Each account has exactly one currency: INR or USD.

## Feature: Create Payment (with Account Selection)

Acceptance Criteria
- User can select a source account from a dropdown populated with their own accounts.
- User must enter a destination account (8-20 alphanumeric characters); source and destination cannot be the same account.
- Amount is mandatory, must be greater than 0, and no more than 1,000,000.
- Currency is automatically set to match the selected source account's currency and cannot be changed independently.
- Reference/description is optional (max 255 characters).
- On submit, the payment is created and the final status (COMPLETED or FAILED) is shown to the user.
- Submitting the same request twice (double-click / retry) never creates a second payment record.

## Feature: Automatic Payment Lifecycle Processing

Acceptance Criteria
- Every new payment starts in CREATED status.
- The payment automatically progresses to VALIDATED (or FAILED, with a reason) without any manual action.
- A VALIDATED payment automatically progresses to SENT then COMPLETED (or FAILED at either stage, with a reason).
- A payment can never skip a stage or move backwards (e.g. COMPLETED can never become CREATED again).
- Every stage transition is recorded with an exact timestamp.

## Feature: Payment Status & Details View

Acceptance Criteria
- User can open a specific payment and see: ID, source account, destination account, amount, currency, reference, current status, created/updated timestamps.
- If the payment is FAILED, the error code and a human-readable error message are both shown.
- If the payment ID doesn't exist, a clear "not found" message is shown (no technical error/stack trace).

## Feature: Payment Status History / Audit Trail

Acceptance Criteria
- User can view the complete, ordered history of status changes for a payment.
- Each history entry shows: previous status, new status, timestamp, and error details (if that transition was a failure).
- History entries are never edited or deleted once written.
- The very first history entry (CREATED) has no "previous status."

## Feature: Payment List with Status Filter

Acceptance Criteria
- User can see a paginated list of all payments (default: newest first).
- User can filter the list to show only one status at a time (CREATED, VALIDATED, SENT, COMPLETED, or FAILED).
- Filter can be cleared to show all payments again.
- List clearly shows a color-coded status badge for each row.

## Feature: Search Payments

Acceptance Criteria
- User can search by payment ID and get an exact match.
- User can search by reference/description text and get a partial, case-insensitive match.
- Search can be combined with the status filter.
- A search with no results shows a clear "no payments found" message, not an error.

## Feature: Duplicate Payment Protection (Idempotency)

Acceptance Criteria
- Submitting the exact same in-flight request twice (e.g. due to double-click or network retry) returns the original payment, not a new one.
- A deliberate, separate payment with identical field values (submitted intentionally, e.g. days apart) is correctly created as a new, independent payment.
- The user is never shown a confusing duplicate error for a legitimate accidental retry - it resolves silently to the original payment.

## Feature: Consistent Validation & Error Messaging

Acceptance Criteria
- Every validation failure returns a specific, predictable error code and a human-readable message (not a generic "something went wrong").
- Error responses never expose internal technical details (no stack traces, no internal exception names).
- The same category of error always produces the same error code, everywhere in the system.

## Feature: API Rate Limiting

Acceptance Criteria
- The system enforces a request-rate ceiling designed for a peak of approximately 40,000 requests/minute.
- When the limit is exceeded, the caller receives a clear "please slow down" response (not a crash, hang, or generic server error).
- The response indicates how long to wait before retrying.
- Legitimate traffic under the limit is completely unaffected.

## Feature: INR & USD Currency Support

Acceptance Criteria
- Only INR and USD are accepted as valid currencies anywhere in the system.
- Any other currency code is rejected with a clear error.
- No currency conversion is performed or implied between INR and USD.

