CREATE TABLE customer (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 160),
    phone TEXT CHECK (phone IS NULL OR length(phone) <= 40),
    address TEXT CHECK (address IS NULL OR length(address) <= 240),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE invoice_counter (
    counter_name TEXT PRIMARY KEY,
    next_value INTEGER NOT NULL CHECK (next_value > 0)
);

INSERT INTO invoice_counter (counter_name, next_value) VALUES ('SALE', 1);

CREATE TABLE sale (
    id TEXT PRIMARY KEY,
    customer_id TEXT REFERENCES customer(id),
    sale_date TEXT NOT NULL,
    invoice_number INTEGER NOT NULL UNIQUE CHECK (invoice_number > 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'QR', 'CREDIT')),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa > 0),
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE TABLE sale_line (
    id TEXT PRIMARY KEY,
    sale_id TEXT NOT NULL REFERENCES sale(id),
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    quantity_sold_base_units INTEGER NOT NULL CHECK (quantity_sold_base_units > 0),
    unit_sale_price_paisa INTEGER NOT NULL CHECK (unit_sale_price_paisa > 0),
    line_total_paisa INTEGER NOT NULL CHECK (line_total_paisa > 0)
);

DROP VIEW batch_stock;

CREATE TABLE inventory_movement_next (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    movement_type TEXT NOT NULL CHECK (
        movement_type IN ('PURCHASE_RECEIPT', 'SALE', 'SALE_RETURN')
    ),
    quantity_base_units INTEGER NOT NULL CHECK (quantity_base_units > 0),
    reference_id TEXT NOT NULL,
    created_at TEXT NOT NULL
);

INSERT INTO inventory_movement_next (
    id, batch_id, movement_type, quantity_base_units, reference_id, created_at
)
SELECT id, batch_id, movement_type, quantity_base_units, reference_id, created_at
FROM inventory_movement;

DROP TABLE inventory_movement;
ALTER TABLE inventory_movement_next RENAME TO inventory_movement;

CREATE INDEX idx_customer_active_name ON customer(is_active, name COLLATE NOCASE);
CREATE INDEX idx_sale_date ON sale(sale_date DESC);
CREATE INDEX idx_sale_customer ON sale(customer_id);
CREATE INDEX idx_sale_line_sale ON sale_line(sale_id);
CREATE INDEX idx_sale_line_batch ON sale_line(batch_id);
CREATE INDEX idx_inventory_movement_batch ON inventory_movement(batch_id);
CREATE INDEX idx_inventory_movement_reference ON inventory_movement(reference_id);

CREATE VIEW batch_stock AS
SELECT
    batch_id,
    COALESCE(SUM(
        CASE movement_type
            WHEN 'SALE' THEN -quantity_base_units
            WHEN 'PURCHASE_RECEIPT' THEN quantity_base_units
            WHEN 'SALE_RETURN' THEN quantity_base_units
        END
    ), 0) AS quantity_base_units
FROM inventory_movement
GROUP BY batch_id;
