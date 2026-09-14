package com.nepalpharmacy.inventory;

import java.time.LocalDate;
import java.util.List;

public interface InventoryAlertRepository {

    InventoryAlertSummary loadSummary(LocalDate asOfDate);

    List<ExpiryBatchAlert> findExpiryAlerts(
            LocalDate asOfDate, InventoryAlertCriteria criteria, int limit);

    List<ProductStockAlert> findProductStockAlerts(
            LocalDate asOfDate, InventoryAlertCriteria criteria, int limit);
}
