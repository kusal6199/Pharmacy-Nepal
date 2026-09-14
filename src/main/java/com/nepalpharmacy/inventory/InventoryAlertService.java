package com.nepalpharmacy.inventory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class InventoryAlertService {

    public static final int RESULT_LIMIT = 150;

    private final InventoryAlertRepository repository;
    private final Clock clock;

    public InventoryAlertService(InventoryAlertRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public InventoryAlertService(InventoryAlertRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InventoryAlertDashboard loadDashboard(InventoryAlertCriteria input) {
        return loadDashboard(LocalDate.now(clock), input);
    }

    public InventoryAlertDashboard loadDashboard(
            LocalDate asOfDate, InventoryAlertCriteria input) {
        Objects.requireNonNull(asOfDate, "asOfDate");
        InventoryAlertCriteria criteria = input == null
                ? InventoryAlertCriteria.defaults() : input.normalized();
        InventoryAlertSummary summary = repository.loadSummary(asOfDate);
        BoundedAlertResult<ExpiryBatchAlert> expiry = limited(
                repository.findExpiryAlerts(asOfDate, criteria, RESULT_LIMIT + 1));
        BoundedAlertResult<ProductStockAlert> stock = limited(
                repository.findProductStockAlerts(asOfDate, criteria, RESULT_LIMIT + 1));
        return new InventoryAlertDashboard(asOfDate, summary, expiry, stock);
    }

    private static <T> BoundedAlertResult<T> limited(List<T> rows) {
        boolean truncated = rows.size() > RESULT_LIMIT;
        return new BoundedAlertResult<>(
                truncated ? rows.subList(0, RESULT_LIMIT) : rows, truncated);
    }
}
