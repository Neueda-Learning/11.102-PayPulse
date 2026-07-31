# 6. Questions for Next Meeting - PayPulse MVP

Prepared for: Customer Review Meeting.
Note: There is no instructor on this project - the only external stakeholder we need answers from is the Customer.

## Business Questions

1. Duplicate submissions: if a user accidentally submits the same payment twice, should we silently return the original payment (our current plan), or show an explicit "duplicate" error message?
2. Account balances: should accounts carry a real balance (enabling a genuine "insufficient funds" check), or is balance out of scope for this phase, with accounts being purely an identity/selection concept?
3. Failure simulation for demos: should test/demo failures be triggered by specific reserved accounts (predictable, good for demos), happen randomly (more realistic), or both?
4. Search behavior: should reference/description search be partial-match (e.g. searching "invoice" finds "Invoice #2024-09"), or exact-match only?
5. Rate limit interpretation: is the ~40,000 requests/minute figure meant as the total system-wide capacity, or as an allowance per individual client/integration?
6. Which "Good to Have" feature matters most to prioritize next: analytics/trend chart, CSV export, or sortable columns?
7. Is there a target timeline/date for when multi-currency conversion (beyond INR/USD) might become a real business requirement?
8. Do you expect a future need for multiple distinct users/roles (e.g. a maker-checker approval flow), or will a single operator remain sufficient for the foreseeable future?

## Technical Assumptions (please confirm or correct)

9. No login/authentication system in this phase - a single operator uses the platform directly.
10. Accounts are pre-provisioned (seeded) for the MVP - there is no "create new account" screen yet.
11. Payment processing to the destination system is simulated internally - there is no real payment network/gateway integration in this phase.
12. The full payment lifecycle (CREATED to VALIDATED to SENT to COMPLETED/FAILED) completes synchronously/instantly for now, rather than showing a multi-second "in progress" animation.
13. Reporting/analytics compute on demand from live data - there is no separate historical data warehouse in this phase.
14. Rate limiting is enforced within the application itself for this phase, with an infrastructure-level gateway limiter noted as a possible later upgrade, not required now.

## Open Decisions Needing Customer Validation

15. Confirm the final list of MVP features (Section 1) - are there any Must Have items that should move to Good to Have/Future, or vice versa?
16. Confirm the wireframes (Section 4) reflect the intended user experience before real development begins - any changes needed to the screen layouts or flow?
17. Confirm acceptable currencies remain INR and USD only for this phase - no other currency should be silently supported.
18. Confirm whether a written Service Level Agreement (e.g. maximum response time, uptime target) is expected alongside the ~40,000 requests/minute figure.
19. Confirm sign-off process: who from the Customer's side reviews and approves this feature list/wireframe set before development starts?

