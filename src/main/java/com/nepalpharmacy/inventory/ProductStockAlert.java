package com.nepalpharmacy.inventory;

import com.nepalpharmacy.product.UnitOfSale;

import java.util.Objects;
import java.util.UUID;

public record ProductStockAlert(
        UUID productId,
        ProductStockStatus status,
        String productName,
        String genericName,
        String manufacturer,
        long sellableStockBaseUnits,
        long physicalStockBaseUnits,
        long expiredStockBaseUnits,
        int reorderThresholdBaseUnits,
        UnitOfSale unitOfSale
) {
    public ProductStockAlert {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(unitOfSale, "unitOfSale");
    }
}
