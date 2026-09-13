CREATE TABLE product_next (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 160),
    generic_name TEXT CHECK (generic_name IS NULL OR length(generic_name) <= 160),
    manufacturer TEXT CHECK (manufacturer IS NULL OR length(manufacturer) <= 160),
    category TEXT NOT NULL CHECK (
        category IN ('TABLET', 'SYRUP', 'INJECTION', 'OINTMENT', 'CAPSULE', 'OTHER')
    ),
    unit_of_sale TEXT NOT NULL CHECK (
        unit_of_sale IN ('TABLET', 'CAPSULE', 'MILLILITRE', 'STRIP', 'BOTTLE', 'VIAL', 'TUBE', 'OTHER')
    ),
    pack_size INTEGER CHECK (pack_size IS NULL OR pack_size > 0),
    purchase_price_paisa INTEGER NOT NULL DEFAULT 0 CHECK (purchase_price_paisa >= 0),
    sale_price_paisa INTEGER NOT NULL CHECK (sale_price_paisa > 0),
    mrp_paisa INTEGER CHECK (mrp_paisa IS NULL OR mrp_paisa >= 0),
    tax_rate_basis_points INTEGER NOT NULL DEFAULT 0
        CHECK (tax_rate_basis_points BETWEEN 0 AND 10000),
    reorder_threshold_base_units INTEGER NOT NULL DEFAULT 0
        CHECK (reorder_threshold_base_units >= 0),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- V1 contained a preliminary product shape. Preserve any development data while
-- moving prices and units into the product-master model required by this slice.
INSERT INTO product_next (
    id,
    name,
    generic_name,
    manufacturer,
    category,
    unit_of_sale,
    pack_size,
    purchase_price_paisa,
    sale_price_paisa,
    mrp_paisa,
    tax_rate_basis_points,
    reorder_threshold_base_units,
    is_active,
    created_at,
    updated_at
)
SELECT
    p.id,
    p.brand_name,
    p.generic_name,
    p.manufacturer,
    CASE upper(trim(coalesce(p.dosage_form, '')))
        WHEN 'TABLET' THEN 'TABLET'
        WHEN 'SYRUP' THEN 'SYRUP'
        WHEN 'INJECTION' THEN 'INJECTION'
        WHEN 'OINTMENT' THEN 'OINTMENT'
        WHEN 'CAPSULE' THEN 'CAPSULE'
        ELSE 'OTHER'
    END,
    CASE upper(trim(coalesce(p.dosage_form, '')))
        WHEN 'TABLET' THEN 'TABLET'
        WHEN 'CAPSULE' THEN 'CAPSULE'
        WHEN 'SYRUP' THEN 'MILLILITRE'
        WHEN 'INJECTION' THEN 'VIAL'
        WHEN 'OINTMENT' THEN 'TUBE'
        ELSE 'OTHER'
    END,
    p.base_units_per_pack,
    coalesce((
        SELECT pb.purchase_price_paisa
        FROM product_batch pb
        WHERE pb.product_id = p.id
        ORDER BY pb.created_at DESC
        LIMIT 1
    ), 0),
    coalesce((
        SELECT pb.selling_price_paisa
        FROM product_batch pb
        WHERE pb.product_id = p.id
        ORDER BY pb.created_at DESC
        LIMIT 1
    ), 1),
    (
        SELECT pb.mrp_paisa
        FROM product_batch pb
        WHERE pb.product_id = p.id
        ORDER BY pb.created_at DESC
        LIMIT 1
    ),
    0,
    0,
    p.active,
    p.created_at,
    p.created_at
FROM product p;

DROP TABLE product;
ALTER TABLE product_next RENAME TO product;

CREATE UNIQUE INDEX ux_product_active_name_manufacturer
    ON product(lower(trim(name)), lower(trim(coalesce(manufacturer, ''))))
    WHERE is_active = 1;

CREATE INDEX idx_product_name ON product(name COLLATE NOCASE);
CREATE INDEX idx_product_active ON product(is_active);

