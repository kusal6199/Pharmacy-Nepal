package com.nepalpharmacy.inventory;

public record InventoryAlertSummary(
        int expiredBatchCount,
        int days0To30BatchCount,
        int days31To60BatchCount,
        int days61To90BatchCount,
        int lowStockProductCount,
        int outOfStockProductCount
) {
}
