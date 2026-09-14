package com.nepalpharmacy.inventory;

import java.time.LocalDate;
import java.util.Objects;

public record InventoryAlertDashboard(
        LocalDate asOfDate,
        InventoryAlertSummary summary,
        BoundedAlertResult<ExpiryBatchAlert> expiryAlerts,
        BoundedAlertResult<ProductStockAlert> productStockAlerts
) {
    public InventoryAlertDashboard {
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(expiryAlerts, "expiryAlerts");
        Objects.requireNonNull(productStockAlerts, "productStockAlerts");
    }
}
