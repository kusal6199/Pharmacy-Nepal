package com.nepalpharmacy.purchasing;

import java.util.Objects;
import java.util.UUID;

public record PurchaseReturnLineAvailability(
        PurchaseLine originalLine,
        UUID productId,
        long previouslyReturnedBaseUnits,
        long availableBatchQuantityBaseUnits
) {

    public PurchaseReturnLineAvailability {
        Objects.requireNonNull(originalLine, "originalLine");
        Objects.requireNonNull(productId, "productId");
        if (previouslyReturnedBaseUnits < 0) {
            throw new IllegalArgumentException("Previously returned quantity cannot be negative.");
        }
    }

    public long remainingReturnableBaseUnits() {
        return originalLine.quantityReceivedBaseUnits() - previouslyReturnedBaseUnits;
    }
}
