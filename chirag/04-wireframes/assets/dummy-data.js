/*
 * PayPulse Wireframe - Dummy JSON Data
 * ------------------------------------
 * This file simulates the data that will eventually come from the real backend API
 * (see ../../docs/openapi.yaml). It exists purely so the frontend wireframes/prototype
 * can be clicked through and validated with the Customer BEFORE any backend exists.
 * When the backend is ready, this file is deleted and replaced with real fetch() calls -
 * the HTML/CSS/JS structure of each page should not need to change.
 */

window.DUMMY_DATA = {

  accounts: [
    { id: "acc-001", label: "Primary INR Savings", accountNumber: "ACC1000001", currency: "INR", status: "ACTIVE" },
    { id: "acc-002", label: "USD Wallet",           accountNumber: "ACC2000002", currency: "USD", status: "ACTIVE" },
    { id: "acc-003", label: "Old INR Account",      accountNumber: "ACC3000003", currency: "INR", status: "INACTIVE" }
  ],

  kpis: {
    totalPayments: 1287,
    successRatePct: 94.2,
    failureRatePct: 5.8,
    avgProcessingTimeSeconds: 0.84,
    throughputPerMinute: 53.6,
    volumeByCurrency: { INR: 1845200.00, USD: 42310.50 },
    topFailureReasons: { NETWORK_ERROR: 41, INVALID_CURRENCY: 12, PROCESSING_ERROR: 9 }
  },

  trend: [
    { hour: "09:00", created: 40, completed: 35, failed: 5 },
    { hour: "10:00", created: 52, completed: 48, failed: 4 },
    { hour: "11:00", created: 61, completed: 55, failed: 6 },
    { hour: "12:00", created: 58, completed: 50, failed: 8 },
    { hour: "13:00", created: 45, completed: 41, failed: 4 },
    { hour: "14:00", created: 70, completed: 64, failed: 6 },
    { hour: "15:00", created: 66, completed: 60, failed: 6 },
    { hour: "16:00", created: 49, completed: 46, failed: 3 }
  ],

  payments: [
    {
      id: "a1b2c3d4-0001", sourceAccountId: "acc-001", destinationAccount: "ACC9998887",
      amount: 2500.00, currency: "INR", reference: "Invoice #4471",
      status: "COMPLETED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T09:12:00Z", updatedAt: "2026-07-31T09:12:01Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:12:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:12:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:12:01Z" },
        { previousStatus: "SENT", newStatus: "COMPLETED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:12:01Z" }
      ]
    },
    {
      id: "a1b2c3d4-0002", sourceAccountId: "acc-002", destinationAccount: "ACC7776665",
      amount: 480.00, currency: "USD", reference: "Supplier payment - Sept",
      status: "COMPLETED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T09:30:00Z", updatedAt: "2026-07-31T09:30:01Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:30:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:30:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:30:01Z" },
        { previousStatus: "SENT", newStatus: "COMPLETED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T09:30:01Z" }
      ]
    },
    {
      id: "a1b2c3d4-0003", sourceAccountId: "acc-001", destinationAccount: "ACC1112223",
      amount: 15000.00, currency: "INR", reference: "Rent - August",
      status: "FAILED", errorCode: "NETWORK_ERROR", errorMessage: "Simulated network failure while transmitting payment",
      createdAt: "2026-07-31T10:02:00Z", updatedAt: "2026-07-31T10:02:02Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:02:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:02:00Z" },
        { previousStatus: "VALIDATED", newStatus: "FAILED", errorCode: "NETWORK_ERROR", errorMessage: "Simulated network failure while transmitting payment", occurredAt: "2026-07-31T10:02:02Z" }
      ]
    },
    {
      id: "a1b2c3d4-0004", sourceAccountId: "acc-002", destinationAccount: "ACC4445556",
      amount: 120.75, currency: "USD", reference: "",
      status: "FAILED", errorCode: "PROCESSING_ERROR", errorMessage: "Simulated processing failure after send",
      createdAt: "2026-07-31T10:15:00Z", updatedAt: "2026-07-31T10:15:02Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:15:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:15:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:15:01Z" },
        { previousStatus: "SENT", newStatus: "FAILED", errorCode: "PROCESSING_ERROR", errorMessage: "Simulated processing failure after send", occurredAt: "2026-07-31T10:15:02Z" }
      ]
    },
    {
      id: "a1b2c3d4-0005", sourceAccountId: "acc-001", destinationAccount: "ACC2223334",
      amount: 999.99, currency: "INR", reference: "Office supplies",
      status: "SENT", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T10:40:00Z", updatedAt: "2026-07-31T10:40:01Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:40:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:40:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:40:01Z" }
      ]
    },
    {
      id: "a1b2c3d4-0006", sourceAccountId: "acc-002", destinationAccount: "ACC5556667",
      amount: 250.00, currency: "USD", reference: "Invoice #5012",
      status: "VALIDATED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T10:55:00Z", updatedAt: "2026-07-31T10:55:00Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:55:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T10:55:00Z" }
      ]
    },
    {
      id: "a1b2c3d4-0007", sourceAccountId: "acc-001", destinationAccount: "ACC6667778",
      amount: 5000.00, currency: "INR", reference: "Consulting fee",
      status: "CREATED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T11:01:00Z", updatedAt: "2026-07-31T11:01:00Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T11:01:00Z" }
      ]
    },
    {
      id: "a1b2c3d4-0008", sourceAccountId: "acc-002", destinationAccount: "ACC8889990",
      amount: 75.20, currency: "USD", reference: "Domain renewal",
      status: "COMPLETED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T08:45:00Z", updatedAt: "2026-07-31T08:45:01Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T08:45:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T08:45:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T08:45:01Z" },
        { previousStatus: "SENT", newStatus: "COMPLETED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T08:45:01Z" }
      ]
    },
    {
      id: "a1b2c3d4-0009", sourceAccountId: "acc-001", destinationAccount: "ACC1231231",
      amount: 3200.00, currency: "INR", reference: "Vendor payment",
      status: "FAILED", errorCode: "INVALID_CURRENCY", errorMessage: "Currency does not match source account currency",
      createdAt: "2026-07-31T07:58:00Z", updatedAt: "2026-07-31T07:58:00Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T07:58:00Z" },
        { previousStatus: "CREATED", newStatus: "FAILED", errorCode: "INVALID_CURRENCY", errorMessage: "Currency does not match source account currency", occurredAt: "2026-07-31T07:58:00Z" }
      ]
    },
    {
      id: "a1b2c3d4-0010", sourceAccountId: "acc-002", destinationAccount: "ACC3213214",
      amount: 60.00, currency: "USD", reference: "Team lunch reimbursement",
      status: "COMPLETED", errorCode: null, errorMessage: null,
      createdAt: "2026-07-31T07:20:00Z", updatedAt: "2026-07-31T07:20:01Z",
      history: [
        { previousStatus: null, newStatus: "CREATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T07:20:00Z" },
        { previousStatus: "CREATED", newStatus: "VALIDATED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T07:20:00Z" },
        { previousStatus: "VALIDATED", newStatus: "SENT", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T07:20:01Z" },
        { previousStatus: "SENT", newStatus: "COMPLETED", errorCode: null, errorMessage: null, occurredAt: "2026-07-31T07:20:01Z" }
      ]
    }
  ]
};

