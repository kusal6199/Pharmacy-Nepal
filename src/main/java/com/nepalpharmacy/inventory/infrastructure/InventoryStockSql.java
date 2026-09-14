package com.nepalpharmacy.inventory.infrastructure;

final class InventoryStockSql {

    static final String POSITIVE_PHYSICAL_STOCK = "s.quantity_base_units > 0";
    static final String NOT_EXPIRED_ON_DATE = "b.expiry_date >= ?";
    static final String EXPIRED_ON_DATE = "b.expiry_date < ?";
    static final String POSITIVE_EXPIRED_STOCK =
            POSITIVE_PHYSICAL_STOCK + " AND " + EXPIRED_ON_DATE;
    static final String SELLABLE_ON_DATE =
            POSITIVE_PHYSICAL_STOCK + " AND " + NOT_EXPIRED_ON_DATE;

    private InventoryStockSql() {
    }
}
