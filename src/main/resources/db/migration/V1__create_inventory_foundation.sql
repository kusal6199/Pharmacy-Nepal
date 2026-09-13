CREATE TABLE product (
    id TEXT PRIMARY KEY,
    sku TEXT NOT NULL UNIQUE,
    barcode TEXT UNIQUE,
    brand_name TEXT NOT NULL,
    generic_name TEXT,
    strength TEXT,
    dosage_form TEXT,
    manufacturer TEXT,
    pack_label TEXT NOT NULL,
    base_units_per_pack INTEGER NOT NULL CHECK (base_units_per_pack > 0),
    drug_category TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
        CHECK (drug_category IN ('A', 'B', 'C', 'UNCLASSIFIED')),
    tax_category TEXT NOT NULL DEFAULT 'EXEMPT'
        CHECK (tax_category IN ('EXEMPT', 'VAT', 'OTHER')),
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL
);

CREATE TABLE supplier (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    pan TEXT,
    phone TEXT,
    address TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL
);

CREATE TABLE product_batch (
    id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES product(id),
    supplier_id TEXT REFERENCES supplier(id),
    batch_number TEXT NOT NULL,
    expiry_date TEXT NOT NULL,
    purchase_price_paisa INTEGER NOT NULL CHECK (purchase_price_paisa >= 0),
    selling_price_paisa INTEGER NOT NULL CHECK (selling_price_paisa >= 0),
    mrp_paisa INTEGER CHECK (mrp_paisa IS NULL OR mrp_paisa >= 0),
    created_at TEXT NOT NULL,
    UNIQUE (product_id, batch_number, expiry_date)
);

CREATE TABLE inventory_movement (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL REFERENCES product_batch(id),
    movement_type TEXT NOT NULL CHECK (
        movement_type IN (
            'OPENING', 'PURCHASE', 'SALE', 'SALE_RETURN', 'PURCHASE_RETURN',
            'EXPIRY', 'DAMAGE', 'ADJUSTMENT', 'CANCELLATION'
        )
    ),
    quantity_delta_base_units INTEGER NOT NULL CHECK (quantity_delta_base_units <> 0),
    source_type TEXT,
    source_id TEXT,
    occurred_at TEXT NOT NULL,
    created_by TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_product_batch_product ON product_batch(product_id);
CREATE INDEX idx_product_batch_expiry ON product_batch(expiry_date);
CREATE INDEX idx_inventory_movement_batch ON inventory_movement(batch_id);
CREATE INDEX idx_inventory_movement_occurred ON inventory_movement(occurred_at);

CREATE VIEW batch_stock AS
SELECT
    batch_id,
    COALESCE(SUM(quantity_delta_base_units), 0) AS quantity_base_units
FROM inventory_movement
GROUP BY batch_id;

