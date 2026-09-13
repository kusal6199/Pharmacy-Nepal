package com.nepalpharmacy.sales;

import java.util.Objects;
import java.util.UUID;

public record SalesReturnLineAvailability(
        SaleLine originalLine,
        UUID productId,
        long previouslyReturnedBaseUnits
) {

    public SalesReturnLineAvailability {
        Objects.requireNonNull(originalLine, "originalLine");
        Objects.requireNonNull(productId, "productId");
        if (previouslyReturnedBaseUnits < 0) {
            throw new IllegalArgumentException("Previously returned quantity cannot be negative.");
        }
    }

    public long remainingReturnableBaseUnits() {
        return originalLine.quantitySoldBaseUnits() - previouslyReturnedBaseUnits;
    }
}
