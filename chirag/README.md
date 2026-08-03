# 📦 Chirag — Customer Review Meeting Deliverables

> This folder contains everything prepared for the **next customer review meeting** for PayPulse (Payments Processing System).
>
> **Important note on terminology:** This is a real customer engagement, not a college/training project. There is **no instructor** involved anywhere in this project — the only external stakeholder is the **Customer**. Every document, diagram, and flow in this folder refers to **"Customer"** only.

## Contents

| # | File | What it covers |
|---|---|---|
| 1 | [`01-feature-list.md`](./01-feature-list.md) | Complete MVP feature list — name, description, priority (Must Have / Good to Have / Future) |
| 2 | [`02-user-stories.md`](./02-user-stories.md) | User stories in `As a <role>, I want <goal> so that <value>` format |
| 3 | [`03-acceptance-criteria.md`](./03-acceptance-criteria.md) | Acceptance criteria per feature |
| 4 | [`04-wireframes/`](./04-wireframes/) | Low-fidelity HTML wireframes/prototype (dummy JSON data — real data will come from the backend API later) |
| 5 | [`05-screen-flow.md`](./05-screen-flow.md) | Navigation / screen flow diagrams |
| 6 | [`06-questions-for-next-meeting.md`](./06-questions-for-next-meeting.md) | Business questions, technical assumptions, and open decisions for the Customer |

## How to view the wireframes

Open `04-wireframes/index.html` in any browser (double-click it, no server needed). It's a clickable HTML prototype:

- **Dashboard** (landing page, KPIs) → **Create Payment** → **Payment Details**
- **Payments** (list, filter, search) → **Payment Details**
- **Accounts** (the customer's own accounts — source-account picker source)

All data on these pages is **hardcoded dummy JSON** (`04-wireframes/assets/dummy-data.js`) purely to validate the user journey. The real backend API (see `../docs/openapi.yaml`) will replace this data source once development starts — no UI/UX rework should be needed, only swapping the data layer.

## Source material

This deliverable set is derived from the project's existing design docs (`../docs/`), reframed for a customer-facing review: `01-CONTEXT.md`, `04-SRS.md`, `11-API-DESIGN.md`, `12-CLARIFICATION-QUESTIONS.md`, and the `../customer-discussion.html` meeting record.

