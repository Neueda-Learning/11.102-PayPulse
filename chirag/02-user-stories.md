# 2. User Stories - PayPulse MVP

Format: As a &lt;user role&gt;, I want &lt;functionality&gt; so that &lt;business value&gt;.

Roles used in these stories:
- Payment Operator - the primary user who submits and manages payments day-to-day.
- Business Stakeholder - a user (could be the same person, different hat) who reviews how the platform is performing.
- Customer - the external stakeholder who owns this product and validates it meets business needs. (Note: there is no "instructor" role anywhere in this project - the Customer is the only external stakeholder.)

## Dashboard & Analytics

1. As a Business Stakeholder, I want to see a KPI dashboard as soon as I open the app, so that I immediately understand how the payment platform is performing without digging through data.
2. As a Business Stakeholder, I want to see the success rate and failure rate of payments, so that I can quickly judge platform health.
3. As a Business Stakeholder, I want to see the average payment processing time, so that I can judge whether the platform is fast enough for our needs.
4. As a Business Stakeholder, I want to see total transaction volume broken down by currency (INR and USD), so that I understand the scale of money moving through the platform in each currency.
5. As a Business Stakeholder, I want to see the top reasons payments are failing, so that I can raise the right issues with the technical team.

## Accounts

6. As a Payment Operator, I want to see all my own accounts (with their currency and status), so that I know which accounts are available to pay from.
7. As a Payment Operator, I want to select a source account from a dropdown when creating a payment, so that I don't have to remember or type account numbers manually.
8. As a Payment Operator, I want an inactive account to be clearly marked and unselectable as a source, so that I don't accidentally try to pay from an account that can't be used.

## Create Payment

9. As a Payment Operator, I want to enter a destination account, amount, and optional reference and submit a payment, so that I can pay someone quickly and easily.
10. As a Payment Operator, I want the currency field to automatically match my selected source account's currency, so that I can't accidentally submit a mismatched-currency payment.
11. As a Payment Operator, I want clear, specific error messages when my payment submission is invalid (e.g. negative amount, unsupported currency, inactive account), so that I know exactly what to fix.
12. As a Payment Operator, I want the system to prevent my payment from being submitted twice if I double-click Submit or my connection is flaky, so that I never accidentally pay the same amount twice by mistake.

## Payment Status & History

13. As a Payment Operator, I want to see the final status of my payment right after I submit it, so that I know immediately whether it succeeded or failed.
14. As a Payment Operator, I want to view the full status history of a payment (every stage it passed through, with timestamps), so that I can understand exactly what happened and when.
15. As a Payment Operator, I want to see a clear error code and description when a payment fails, so that I understand why it failed and what I might do next.

## Payment List, Filter & Search

16. As a Payment Operator, I want to see a list of all my payments, so that I have a complete record of my payment activity.
17. As a Payment Operator, I want to filter the payment list by status (e.g. show only FAILED payments), so that I can quickly find payments that need my attention.
18. As a Payment Operator, I want to search for a payment by its ID or by reference text, so that I can quickly locate a specific payment without scrolling through the entire list.

## Platform Reliability & Protection

19. As a Customer, I want the platform to keep working reliably even under heavy traffic (up to ~40,000 requests/minute), so that our business operations are never disrupted by load spikes.
20. As a Customer, I want the platform to reject excessive/abusive traffic gracefully (with a clear "please slow down" response) rather than crashing or slowing down for everyone, so that the system stays available and fair to all legitimate use.
21. As a Customer, I want assurance that the API is protected against common security attacks (e.g. injection attacks, oversized requests), so that our financial data and operations stay safe.

## Future / Good-to-Have (for context only - not committed MVP scope)

22. As a Business Stakeholder, I want to see a simple trend chart of payment volume over the last 24 hours, so that I can spot unusual spikes or drops at a glance.
23. As a Payment Operator, I want to export the current payment list to CSV, so that I can do offline reporting/analysis in a spreadsheet.
24. As a Payment Operator, I want to cancel a payment while it is still CREATED, so that I can stop a mistaken submission before it's processed.

