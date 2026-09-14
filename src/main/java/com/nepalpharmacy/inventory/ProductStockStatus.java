package com.nepalpharmacy.inventory;

public enum ProductStockStatus {
    OUT_OF_STOCK("Out of stock"),
    LOW_STOCK("Low stock"),
    OK("OK");

    private final String displayName;

    ProductStockStatus(String displayName) {
        this.displayName = displayName;
    }

    public static ProductStockStatus classify(
            long sellableStockBaseUnits, int reorderThresholdBaseUnits) {
        if (sellableStockBaseUnits <= 0) {
            return OUT_OF_STOCK;
        }
        if (reorderThresholdBaseUnits > 0
                && sellableStockBaseUnits <= reorderThresholdBaseUnits) {
            return LOW_STOCK;
        }
        return OK;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
