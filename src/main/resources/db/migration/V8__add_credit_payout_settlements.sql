-- V8 corrective follow-up: add both missing directions for settling negative
-- customer/supplier credit while preserving immutable, derived subledgers.

-- V6 intended manual payments to require Cash/QR, but SQLite accepts a CHECK
-- expression whose result is NULL. Application writes were already validated;
-- these guards make any out-of-band ambiguous row fail before a rebuild rather
-- than guessing its method or partially changing the database.
CREATE TEMP TABLE v8_customer_payment_method_preflight (
    repair_null_payment_method_before_v8 TEXT NOT NULL
);
INSERT INTO v8_customer_payment_method_preflight (repair_null_payment_method_before_v8)
SELECT payment_method
FROM customer_account_entry
WHERE entry_type = 'PAYMENT_RECEIVED' AND payment_method IS NULL;
DROP TABLE v8_customer_payment_method_preflight;

CREATE TEMP TABLE v8_supplier_payment_method_preflight (
    repair_null_payment_method_before_v8 TEXT NOT NULL
);
INSERT INTO v8_supplier_payment_method_preflight (repair_null_payment_method_before_v8)
SELECT payment_method
FROM supplier_account_entry
WHERE entry_type = 'PAYMENT_MADE' AND payment_method IS NULL;
DROP TABLE v8_supplier_payment_method_preflight;

CREATE TABLE customer_account_entry_next (
    id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES customer(id),
    entry_date TEXT NOT NULL,
    entry_type TEXT NOT NULL CHECK (
        entry_type IN ('OPENING_BALANCE', 'PAYMENT_RECEIVED', 'CREDIT_PAYOUT')
    ),
    amount_paisa INTEGER NOT NULL CHECK (amount_paisa > 0),
    payment_method TEXT CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'QR')
    ),
    reference_text TEXT CHECK (
        reference_text IS NULL OR length(reference_text) <= 160
    ),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 500),
    created_at TEXT NOT NULL,
    created_by TEXT,
    CHECK (
        (entry_type = 'OPENING_BALANCE' AND payment_method IS NULL)
        OR
        (entry_type IN ('PAYMENT_RECEIVED', 'CREDIT_PAYOUT')
            AND payment_method IS NOT NULL
            AND payment_method IN ('CASH', 'QR'))
    )
);

INSERT INTO customer_account_entry_next (
    id, customer_id, entry_date, entry_type, amount_paisa, payment_method,
    reference_text, notes, created_at, created_by
)
SELECT id, customer_id, entry_date, entry_type, amount_paisa, payment_method,
       reference_text, notes, created_at, created_by
FROM customer_account_entry;

DROP TABLE customer_account_entry;
ALTER TABLE customer_account_entry_next RENAME TO customer_account_entry;

CREATE UNIQUE INDEX ux_customer_account_opening
    ON customer_account_entry(customer_id)
    WHERE entry_type = 'OPENING_BALANCE';
CREATE INDEX idx_customer_account_party_date
    ON customer_account_entry(customer_id, entry_date, created_at, id);

CREATE TABLE supplier_account_entry_next (
    id TEXT PRIMARY KEY,
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    entry_date TEXT NOT NULL,
    entry_type TEXT NOT NULL CHECK (
        entry_type IN ('OPENING_BALANCE', 'PAYMENT_MADE', 'CREDIT_REFUND_RECEIVED')
    ),
    amount_paisa INTEGER NOT NULL CHECK (amount_paisa > 0),
    payment_method TEXT CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'QR')
    ),
    reference_text TEXT CHECK (
        reference_text IS NULL OR length(reference_text) <= 160
    ),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 500),
    created_at TEXT NOT NULL,
    created_by TEXT,
    CHECK (
        (entry_type = 'OPENING_BALANCE' AND payment_method IS NULL)
        OR
        (entry_type IN ('PAYMENT_MADE', 'CREDIT_REFUND_RECEIVED')
            AND payment_method IS NOT NULL
            AND payment_method IN ('CASH', 'QR'))
    )
);

INSERT INTO supplier_account_entry_next (
    id, supplier_id, entry_date, entry_type, amount_paisa, payment_method,
    reference_text, notes, created_at, created_by
)
SELECT id, supplier_id, entry_date, entry_type, amount_paisa, payment_method,
       reference_text, notes, created_at, created_by
FROM supplier_account_entry;

DROP TABLE supplier_account_entry;
ALTER TABLE supplier_account_entry_next RENAME TO supplier_account_entry;

CREATE UNIQUE INDEX ux_supplier_account_opening
    ON supplier_account_entry(supplier_id)
    WHERE entry_type = 'OPENING_BALANCE';
CREATE INDEX idx_supplier_account_party_date
    ON supplier_account_entry(supplier_id, entry_date, created_at, id);
