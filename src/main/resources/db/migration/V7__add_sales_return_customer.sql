-- V8 follow-up: allow a Credit refund for a walk-in Cash/QR sale to belong
-- to a customer without mutating the immutable original sale.
ALTER TABLE sales_return
    ADD COLUMN customer_id TEXT REFERENCES customer(id);
