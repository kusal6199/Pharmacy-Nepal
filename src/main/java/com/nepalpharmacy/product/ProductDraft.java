package com.nepalpharmacy.product;

public record ProductDraft(
        String name,
        String genericName,
        String manufacturer,
        ProductCategory category,
        UnitOfSale unitOfSale,
        Integer packSize,
        long purchasePricePaisa,
        long salePricePaisa,
        Long mrpPaisa,
        int taxRateBasisPoints,
        int reorderThresholdBaseUnits,
        boolean active
) {

    public ProductDraft normalized() {
        return new ProductDraft(
                normalizeRequired(name),
                normalizeOptional(genericName),
                normalizeOptional(manufacturer),
                category,
                unitOfSale,
                packSize,
                purchasePricePaisa,
                salePricePaisa,
                mrpPaisa,
                taxRateBasisPoints,
                reorderThresholdBaseUnits,
                active
        );
    }

    private static String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

