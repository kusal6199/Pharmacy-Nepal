package com.nepalpharmacy.product;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(
        UUID id,
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
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        ProductValidator.validate(new ProductDraft(
                name,
                genericName,
                manufacturer,
                category,
                unitOfSale,
                packSize,
                purchasePricePaisa,
                salePricePaisa,
                mrpPaisa,
                taxRateBasisPoints,
                reorderThresholdBaseUnits,
                active
        ));
    }
}

