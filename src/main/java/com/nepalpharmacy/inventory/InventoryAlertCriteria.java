package com.nepalpharmacy.inventory;

public record InventoryAlertCriteria(
        ExpiryHorizon expiryHorizon,
        String expiryText,
        ProductStockFilter productStockFilter,
        String productText
) {
    public InventoryAlertCriteria normalized() {
        return new InventoryAlertCriteria(
                expiryHorizon == null ? ExpiryHorizon.ALL : expiryHorizon,
                normalize(expiryText),
                productStockFilter == null ? ProductStockFilter.ALL : productStockFilter,
                normalize(productText));
    }

    public static InventoryAlertCriteria defaults() {
        return new InventoryAlertCriteria(
                ExpiryHorizon.ALL, null, ProductStockFilter.ALL, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
