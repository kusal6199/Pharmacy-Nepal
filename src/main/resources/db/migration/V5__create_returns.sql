INSERT INTO invoice_counter (counter_name, next_value) VALUES ('SALES_RETURN', 1);
INSERT INTO invoice_counter (counter_name, next_value) VALUES ('PURCHASE_RETURN', 1);

CREATE TABLE sales_return (
    id TEXT PRIMARY KEY,
    return_number INTEGER NOT NULL UNIQUE CHECK (return_number > 0),
    original_sale_id TEXT NOT NULL REFERENCES sale(id),
    return_date TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (
        reason IN ('WRONG_MEDICINE', 'CUSTOMER_RETURN', 'DAMAGED', 'BILLING_ERROR', 'OTHER')
    ),
    refund_method TEXT NOT NULL CHECK (refund_method IN ('CASH', 'QR', 'CREDIT')),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa > 0),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 500),
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE TABLE sales_return_line (
    id TEXT PRIMARY KEY,
    sales_return_id TEXT NOT NULL REFERENCES sales_return(id),
    original_sale_line_id TEXT NOT NULL REFERENCES sale_line(id),
    product_id TEXT NOT NULL REFERENCES product(id),
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    quantity_returned_base_units INTEGER NOT NULL CHECK (quantity_returned_base_units > 0),
    unit_price_paisa INTEGER NOT NULL CHECK (unit_price_paisa > 0),
    line_total_paisa INTEGER NOT NULL CHECK (line_total_paisa > 0),
    UNIQUE (sales_return_id, original_sale_line_id)
);

CREATE TABLE purchase_return (
    id TEXT PRIMARY KEY,
    return_number INTEGER NOT NULL UNIQUE CHECK (return_number > 0),
    original_purchase_id TEXT NOT NULL REFERENCES purchase(id),
    supplier_id TEXT NOT NULL REFERENCES supplier(id),
    return_date TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (
        reason IN ('DAMAGED', 'WRONG_ITEM', 'EXPIRED_ON_RECEIPT', 'SUPPLIER_RECALL', 'OTHER')
    ),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 500),
    total_amount_paisa INTEGER NOT NULL CHECK (total_amount_paisa >= 0),
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE TABLE purchase_return_line (
    id TEXT PRIMARY KEY,
    purchase_return_id TEXT NOT NULL REFERENCES purchase_return(id),
    original_purchase_line_id TEXT NOT NULL REFERENCES purchase_line(id),
    product_id TEXT NOT NULL REFERENCES product(id),
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    quantity_returned_base_units INTEGER NOT NULL CHECK (quantity_returned_base_units > 0),
    unit_cost_paisa INTEGER NOT NULL CHECK (unit_cost_paisa >= 0),
    line_total_paisa INTEGER NOT NULL CHECK (line_total_paisa >= 0),
    UNIQUE (purchase_return_id, original_purchase_line_id)
);

DROP VIEW batch_stock;

CREATE TABLE inventory_movement_next (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    movement_type TEXT NOT NULL CHECK (
        movement_type IN ('PURCHASE_RECEIPT', 'SALE', 'SALE_RETURN', 'PURCHASE_RETURN')
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

CREATE INDEX idx_sales_return_original_sale ON sales_return(original_sale_id);
CREATE INDEX idx_sales_return_line_return ON sales_return_line(sales_return_id);
CREATE INDEX idx_sales_return_line_original ON sales_return_line(original_sale_line_id);
CREATE INDEX idx_sales_return_line_batch ON sales_return_line(batch_id);
CREATE INDEX idx_purchase_return_original_purchase ON purchase_return(original_purchase_id);
CREATE INDEX idx_purchase_return_supplier ON purchase_return(supplier_id);
CREATE INDEX idx_purchase_return_line_return ON purchase_return_line(purchase_return_id);
CREATE INDEX idx_purchase_return_line_original ON purchase_return_line(original_purchase_line_id);
CREATE INDEX idx_purchase_return_line_batch ON purchase_return_line(batch_id);
CREATE INDEX idx_inventory_movement_batch ON inventory_movement(batch_id);
CREATE INDEX idx_inventory_movement_reference ON inventory_movement(reference_id);

CREATE VIEW batch_stock AS
SELECT
    batch_id,
    COALESCE(SUM(
        CASE movement_type
            WHEN 'PURCHASE_RECEIPT' THEN quantity_base_units
            WHEN 'SALE_RETURN' THEN quantity_base_units
            WHEN 'SALE' THEN -quantity_base_units
            WHEN 'PURCHASE_RETURN' THEN -quantity_base_units
        END
    ), 0) AS quantity_base_units
FROM inventory_movement
GROUP BY batch_id;
