# 5. Screen Flow - PayPulse MVP

Prepared for: Customer Review Meeting. Corresponding clickable HTML wireframes are in `04-wireframes/`.

## Primary flow - Submitting a new payment

```
Dashboard (KPIs)
      |
      v
Create Payment  --(select source account from dropdown)-->  Fill form (destination, amount, reference)
      |
      v
Submit
      |
      v
Processing (auto: CREATED -> VALIDATED -> SENT -> COMPLETED/FAILED)
      |
      v
Payment Details  (final status + fields)
      |
      v
Status History  (full timestamped timeline)
```

## Secondary flow - Reviewing existing payments

```
Dashboard (KPIs)
      |
      v
Payments (list, paginated)
      |
      +--> Filter by Status (e.g. FAILED)
      |
      +--> Search (by Payment ID or Reference)
      |
      v
Click a row
      |
      v
Payment Details
      |
      v
Status History
```

## Accounts flow - Understanding available accounts

```
Dashboard (KPIs)
      |
      v
Accounts  (view own accounts: label, number, currency, status)
      |
      v
Create Payment  (accounts already loaded into the source-account dropdown)
```

## Failure / validation flow

```
Create Payment
      |
      v
Submit (invalid input: e.g. negative amount, unsupported currency, inactive account)
      |
      v
Inline validation error shown  --> user corrects the field --> Submit again
```

## Duplicate-submission flow (idempotency)

```
Create Payment
      |
      v
Submit (user double-clicks, or network retries automatically)
      |
      v
System detects same submission attempt
      |
      v
Original payment is returned  --> Payment Details (no duplicate created)
```

## Rate-limit flow (platform protection)

```
Any Screen
      |
      v
Traffic exceeds the allowed rate
      |
      v
"Please slow down" notice shown (with a suggested wait time)
      |
      v
User/system retries after the wait  --> Normal flow resumes
```

## Full navigation map (all screens)

```
                +----------------+
                |   Dashboard    |  (landing page / KPIs)
                +----------------+
                 |      |      |
        +--------+      |      +--------+
        v               v               v
 +-------------+  +-----------+  +--------------+
 |  Accounts   |  |  Payments |  | Create Payment|
 +-------------+  |   (list)  |  +--------------+
                   +-----------+         |
                        |                v
                        v         +----------------+
                 +----------------+ Payment Details |
                 | Payment Details|<---+------------+
                 +----------------+
                        |
                        v
                 +----------------+
                 | Status History |
                 +----------------+
```

Notes for the Customer:
- The Dashboard is deliberately the landing page - business health is visible before any transactional action.
- Every path leads to the same single "Payment Details" screen, keeping the mental model simple.
- There is no login screen in this navigation map - this MVP has a single operator and no authentication (see `06-questions-for-next-meeting.md` for whether this should change in a future phase).

