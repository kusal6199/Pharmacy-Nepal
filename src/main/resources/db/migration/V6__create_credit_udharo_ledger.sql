-- V8: derived party subledgers and explicit supplier-side settlement methods.
-- Existing purchase rows predate settlement tracking and are deliberately marked
-- LEGACY_UNSPECIFIED so they never silently alter a supplier credit balance.

CREATE TABLE purchase_next (
    id TEXT PRIMARY KEY,
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    purchase_date TEXT NOT NULL,
    invoice_number TEXT CHECK (invoice_number IS NULL OR length(invoice_number) <= 80),
    payment_method TEXT NOT NULL CHECK (
        payment_method IN ('CASH', 'QR', 'CREDIT', 'LEGACY_UNSPECIFIED')
    ),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa >= 0),
    created_at TEXT NOT NULL,
    created_by TEXT
);

INSERT INTO purchase_next (
    id, supplier_id, purchase_date, invoice_number, payment_method,
    total_amount_paisa, created_at, created_by
)
SELECT id, supplier_id, purchase_date, invoice_number, 'LEGACY_UNSPECIFIED',
       total_amount_paisa, created_at, created_by
FROM purchase;

DROP TABLE purchase;
ALTER TABLE purchase_next RENAME TO purchase;
CREATE INDEX idx_purchase_supplier_date
    ON purchase(supplier_id, purchase_date DESC);

-- Existing returns likewise have no reliable settlement meaning. They remain
-- visible in history but are excluded from the derived supplier ledger.
CREATE TABLE purchase_return_next (
    id TEXT PRIMARY KEY,
    return_number INTEGER NOT NULL UNIQUE CHECK (return_number > 0),
    original_purchase_id TEXT NOT NULL REFERENCES purchase(id),
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    return_date TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (
        reason IN ('DAMAGED', 'WRONG_ITEM', 'EXPIRED_ON_RECEIPT', 'SUPPLIER_RECALL', 'OTHER')
    ),
    settlement_method TEXT NOT NULL CHECK (
        settlement_method IN ('CASH', 'QR', 'CREDIT', 'LEGACY_UNSPECIFIED')
    ),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 500),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa >= 0),
    created_at TEXT NOT NULL,
    created_by TEXT
);

INSERT INTO purchase_return_next (
    id, return_number, original_purchase_id, supplier_id, return_date,
    reason, settlement_method, notes, total_amount_paisa, created_at, created_by
)
SELECT id, return_number, original_purchase_id, supplier_id, return_date,
       reason, 'LEGACY_UNSPECIFIED', notes, total_amount_paisa, created_at, created_by
FROM purchase_return;

DROP TABLE purchase_return;
ALTER TABLE purchase_return_next RENAME TO purchase_return;
CREATE INDEX idx_purchase_return_original_purchase
    ON purchase_return(original_purchase_id);
CREATE INDEX idx_purchase_return_supplier
    ON purchase_return(supplier_id);

CREATE TABLE customer_account_entry (
    id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES customer(id),
    entry_date TEXT NOT NULL,
    entry_type TEXT NOT NULL CHECK (
        entry_type IN ('OPENING_BALANCE', 'PAYMENT_RECEIVED')
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
        (entry_type = 'PAYMENT_RECEIVED' AND payment_method IN ('CASH', 'QR'))
    )
);

CREATE UNIQUE INDEX ux_customer_account_opening
    ON customer_account_entry(customer_id)
    WHERE entry_type = 'OPENING_BALANCE';
CREATE INDEX idx_customer_account_party_date
    ON customer_account_entry(customer_id, entry_date, created_at, id);

CREATE TABLE supplier_account_entry (
    id TEXT PRIMARY KEY,
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    entry_date TEXT NOT NULL,
    entry_type TEXT NOT NULL CHECK (
        entry_type IN ('OPENING_BALANCE', 'PAYMENT_MADE')
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
        (entry_type = 'PAYMENT_MADE' AND payment_method IN ('CASH', 'QR'))
    )
);

CREATE UNIQUE INDEX ux_supplier_account_opening
    ON supplier_account_entry(supplier_id)
    WHERE entry_type = 'OPENING_BALANCE';
CREATE INDEX idx_supplier_account_party_date
    ON supplier_account_entry(supplier_id, entry_date, created_at, id);
