package com.nepalpharmacy.inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryMovement(
        UUID id,
        UUID batchId,
        InventoryMovementType movementType,
        int quantityBaseUnits,
        UUID referenceId,
        Instant createdAt
) {

    public InventoryMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(movementType, "movementType");
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantityBaseUnits <= 0) {
            throw new IllegalArgumentException("Movement quantity must be greater than zero.");
        }
    }
}
