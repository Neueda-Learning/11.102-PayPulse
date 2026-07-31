-- V3: Seed accounts — 3 accounts matching chirag/04-wireframes/assets/dummy-data.js
-- Owner: M1
-- IDs match the dummy-data.js UUIDs so frontend devs can test against both old and real API.

INSERT INTO account (id, label, account_number, currency, status) VALUES
    ('b2c3d4e5-1111-4a11-8a11-111111111111', 'Primary INR Savings', 'ACC1000001', 'INR', 'ACTIVE'),
    ('c3d4e5f6-2222-4a22-8a22-222222222222', 'USD Wallet',           'ACC2000002', 'USD', 'ACTIVE'),
    ('d4e5f6a7-3333-4a33-8a33-333333333333', 'Old INR Account',      'ACC3000003', 'INR', 'INACTIVE');

