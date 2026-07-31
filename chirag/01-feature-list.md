# 1. Complete Feature List - PayPulse MVP

Prepared for: Customer Review Meeting
Legend - Priority: Must Have (MVP-blocking) / Good to Have (adds value) / Future (post-MVP)

## Must Have (Core MVP)

| # | Feature Name | Description | Priority |
|---|---|---|---|
| 1 | KPI Dashboard (Landing Page) | The first screen users see. Shows business-meaningful metrics: success rate, failure rate, average processing time, throughput, and transaction volume by currency. No vanity metrics. | Must Have |
| 2 | Multi-Account Support | The user owns multiple accounts (e.g. one INR account, one USD account). Accounts are pre-provisioned; each has a label, account number, currency, and active/inactive status. | Must Have |
| 3 | Create Payment (with Account Selection) | User selects a source account from their own accounts (dropdown), enters a destination account, amount, currency (auto-matched to source account), and an optional reference/description, then submits. | Must Have |
| 4 | Automatic Payment Lifecycle Processing | Once submitted, a payment automatically progresses through CREATED to VALIDATED to SENT to COMPLETED, or moves to FAILED at any stage with a recorded reason. | Must Have |
| 5 | Payment Status & Details View | View any payment's current status, all its fields, and (if failed) the error code/message. | Must Have |
| 6 | Payment Status History / Audit Trail | A permanent, timestamped log of every status change a payment went through - nothing is ever overwritten or deleted. | Must Have |
| 7 | Payment List with Status Filter | Browse all payments in a paginated table; filter to show only a specific status (e.g. only FAILED). | Must Have |
| 8 | Search Payments | Search by payment ID (exact match) or by reference/description text (partial match). | Must Have |
| 9 | Duplicate Payment Protection (Idempotency) | Accidental double-clicks or network retries never create two payments for the same submission attempt. | Must Have |
| 10 | Consistent Validation & Error Messaging | Every failure (invalid amount, unsupported currency, inactive account, etc.) returns a clear, structured, predictable error the UI can render meaningfully. | Must Have |
| 11 | API Rate Limiting | The platform protects itself from being overwhelmed by traffic spikes (target: ~40,000 requests/minute) - excess requests are politely rejected rather than crashing the system. | Must Have |
| 12 | INR & USD Currency Support | The system supports exactly two currencies to start: Indian Rupee (INR) and US Dollar (USD). No conversion between them. | Must Have |

## Good to Have

| # | Feature Name | Description | Priority |
|---|---|---|---|
| 13 | Basic Analytics / Trend View | A simple chart showing payment volume and outcomes over the last 24 hours, supporting the KPI dashboard. | Good to Have |
| 14 | Export Payment List (CSV) | Download the current filtered payment list as a CSV file for offline reporting. | Good to Have |
| 15 | Configurable Failure Simulation | For demos/testing, specific reserved test accounts reliably trigger a failure, so failure scenarios can be shown on demand. | Good to Have |
| 16 | Sortable Columns in Payment List | Click a column header (amount, date, status) to sort the payment list. | Good to Have |
| 17 | Copy Payment ID / Deep Linking | One-click copy of a payment ID, and a shareable URL that opens directly to that payment's details. | Good to Have |

## Future (Post-MVP)

| # | Feature Name | Description | Priority |
|---|---|---|---|
| 18 | Payment Cancellation | Cancel a payment while it's still in CREATED status, before it's processed. | Future |
| 19 | Payment Reversal | Reverse a completed payment by creating a new, offsetting payment. | Future |
| 20 | Multi-Currency Conversion (FX) | Allow paying out of one currency's account into another currency, with a live/looked-up exchange rate. | Future |
| 21 | Notifications | Email or webhook alerts when a payment completes or fails. | Future |
| 22 | Batch / Bulk Payments | Submit multiple payments in a single request/file upload, with a batch summary report. | Future |
| 23 | Recurring / Scheduled Payments | Schedule a payment for a future date, or set up a recurring payment. | Future |
| 24 | Full Authentication & Multi-User Roles | Login system supporting multiple distinct users/roles (e.g. maker-checker approval workflows), replacing the current single-operator model. | Future |
| 25 | Account Management Screen | Let the user create/edit/deactivate their own accounts from the UI, instead of accounts being pre-provisioned. | Future |

---

Note: Features 1-12 constitute the committed MVP scope for the next delivery milestone. Features 13-17 will be attempted if time allows without risking the MVP deadline. Features 18-25 are explicitly out of scope for this delivery and are listed here purely for roadmap visibility and to set the Customer's expectations correctly.

