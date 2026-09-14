package com.nepalpharmacy.inventory;

public enum ProductStockFilter {
    ALL("All stock alerts"),
    OUT_OF_STOCK("Out of stock"),
    LOW_STOCK("Low stock");

    private final String displayName;

    ProductStockFilter(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
