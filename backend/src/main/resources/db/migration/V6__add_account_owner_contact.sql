-- V6: Add owner contact fields to account (MEM-026)
ALTER TABLE account
    ADD COLUMN owner_email VARCHAR(255) NULL,
    ADD COLUMN owner_name  VARCHAR(100) NULL;

-- Update seeded accounts with placeholder contact info for demo
UPDATE account SET owner_email = 'rustampavri1275@gmail.com', owner_name = 'Rustam Kumar';