DROP VIEW batch_stock;

-- V1 supplied preliminary supplier and batch tables before purchase entry existed.
-- Rebuild them into the slice's canonical shape while retaining development rows.
CREATE TABLE supplier_next (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 160),
    phone TEXT CHECK (phone IS NULL OR length(phone) <= 40),
    address TEXT CHECK (address IS NULL OR length(address) <= 240),
    pan TEXT CHECK (pan IS NULL OR length(pan) <= 40),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

INSERT INTO supplier_next (id, name, phone, address, pan, is_active, created_at, updated_at)
SELECT id, trim(name), phone, address, pan, active, created_at, created_at
FROM supplier;

CREATE TABLE product_batch_next (
    id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES product(id),
    batch_number TEXT NOT NULL CHECK (length(trim(batch_number)) BETWEEN 1 AND 80),
    expiry_date TEXT NOT NULL,
    manufacturing_date TEXT,
    purchase_price_paisa INTEGER NOT NULL CHECK (purchase_price_paisa >= 0),
    created_at TEXT NOT NULL,
    UNIQUE (product_id, batch_number, expiry_date)
);

INSERT INTO product_batch_next (
    id, product_id, batch_number, expiry_date, manufacturing_date,
    purchase_price_paisa, created_at
)
SELECT id, product_id, trim(batch_number), expiry_date, NULL,
       purchase_price_paisa, created_at
FROM product_batch;

DROP TABLE inventory_movement;
DROP TABLE product_batch;
DROP TABLE supplier;

ALTER TABLE supplier_next RENAME TO supplier;
ALTER TABLE product_batch_next RENAME TO product_batch;

CREATE TABLE purchase (
    id TEXT PRIMARY KEY,
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    purchase_date TEXT NOT NULL,
    invoice_number TEXT CHECK (invoice_number IS NULL OR length(invoice_number) <= 80),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa >= 0),
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE TABLE purchase_line (
    id TEXT PRIMARY KEY,
    purchase_id TEXT NOT NULL REFERENCES purchase(id),
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    quantity_received_base_units INTEGER NOT NULL
        CHECK (quantity_received_base_units > 0),
    unit_purchase_price_paisa INTEGER NOT NULL
        CHECK (unit_purchase_price_paisa >= 0),
    line_total_paisa INTEGER NOT NULL CHECK (line_total_paisa >= 0)
);

CREATE TABLE inventory_movement (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    movement_type TEXT NOT NULL CHECK (movement_type IN ('PURCHASE_RECEIPT')),
    quantity_base_units INTEGER NOT NULL CHECK (quantity_base_units > 0),
    reference_id TEXT NOT NULL REFERENCES purchase(id),
    created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX ux_product_batch_identity
    ON product_batch(product_id, batch_number COLLATE NOCASE, expiry_date);
CREATE INDEX idx_product_batch_product_expiry
    ON product_batch(product_id, expiry_date);
CREATE INDEX idx_purchase_supplier_date
    ON purchase(supplier_id, purchase_date DESC);
CREATE INDEX idx_purchase_line_purchase ON purchase_line(purchase_id);
CREATE INDEX idx_purchase_line_batch ON purchase_line(batch_id);
CREATE INDEX idx_inventory_movement_batch ON inventory_movement(batch_id);
CREATE INDEX idx_inventory_movement_reference ON inventory_movement(reference_id);

CREATE VIEW batch_stock AS
SELECT
    batch_id,
    COALESCE(SUM(quantity_base_units), 0) AS quantity_base_units
FROM inventory_movement
GROUP BY batch_id;
