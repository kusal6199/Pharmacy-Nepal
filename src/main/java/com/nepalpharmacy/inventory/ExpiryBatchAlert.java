package com.nepalpharmacy.inventory;

import com.nepalpharmacy.product.UnitOfSale;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ExpiryBatchAlert(
        UUID batchId,
        ExpiryStatus status,
        String productName,
        String genericName,
        String manufacturer,
        String batchNumber,
        LocalDate expiryDate,
        long daysRemaining,
        long physicalStockBaseUnits,
        UnitOfSale unitOfSale,
        boolean productActive
) {
    public ExpiryBatchAlert {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
        Objects.requireNonNull(unitOfSale, "unitOfSale");
        if (physicalStockBaseUnits <= 0) {
            throw new IllegalArgumentException("Expiry alert stock must be positive.");
        }
    }
}
