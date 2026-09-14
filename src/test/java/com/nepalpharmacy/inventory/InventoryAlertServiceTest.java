package com.nepalpharmacy.inventory;

import com.nepalpharmacy.product.UnitOfSale;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAlertServiceTest {

    @Test
    void fixedClockDrivesEveryQueryAndResultsAreBounded() {
        LocalDate expectedDate = LocalDate.of(2026, 9, 13);
        RecordingRepository repository = new RecordingRepository();
        InventoryAlertService service = new InventoryAlertService(repository, Clock.fixed(
                Instant.parse("2026-09-12T18:15:00Z"), ZoneId.of("Asia/Kathmandu")));

        InventoryAlertDashboard dashboard = service.loadDashboard(new InventoryAlertCriteria(
                null, "  pharma  ", null, "  para  "));

        assertEquals(expectedDate, dashboard.asOfDate());
        assertEquals(List.of(expectedDate, expectedDate, expectedDate), repository.dates);
        assertEquals(InventoryAlertService.RESULT_LIMIT + 1, repository.lastLimit);
        assertEquals(ExpiryHorizon.ALL, repository.criteria.expiryHorizon());
        assertEquals("pharma", repository.criteria.expiryText());
        assertEquals(ProductStockFilter.ALL, repository.criteria.productStockFilter());
        assertEquals("para", repository.criteria.productText());
        assertEquals(InventoryAlertService.RESULT_LIMIT, dashboard.expiryAlerts().rows().size());
        assertEquals(InventoryAlertService.RESULT_LIMIT,
                dashboard.productStockAlerts().rows().size());
        assertTrue(dashboard.expiryAlerts().truncated());
        assertTrue(dashboard.productStockAlerts().truncated());
    }

    private static final class RecordingRepository implements InventoryAlertRepository {
        private final List<ExpiryBatchAlert> expiryRows = IntStream.range(0, 151)
                .mapToObj(index -> new ExpiryBatchAlert(
                        UUID.randomUUID(), ExpiryStatus.DAYS_0_TO_30, "Product " + index,
                        null, null, "B-" + index, LocalDate.of(2026, 9, 13), 0,
                        1, UnitOfSale.TABLET, true))
                .toList();
        private final List<ProductStockAlert> stockRows = IntStream.range(0, 151)
                .mapToObj(index -> new ProductStockAlert(
                        UUID.randomUUID(), ProductStockStatus.OUT_OF_STOCK,
                        "Product " + index, null, null, 0, 0, 0, 0,
                        UnitOfSale.TABLET))
                .toList();
        private final java.util.ArrayList<LocalDate> dates = new java.util.ArrayList<>();
        private InventoryAlertCriteria criteria;
        private int lastLimit;

        @Override
        public InventoryAlertSummary loadSummary(LocalDate asOfDate) {
            dates.add(asOfDate);
            return new InventoryAlertSummary(0, 0, 0, 0, 0, 0);
        }

        @Override
        public List<ExpiryBatchAlert> findExpiryAlerts(
                LocalDate asOfDate, InventoryAlertCriteria criteria, int limit) {
            dates.add(asOfDate);
            this.criteria = criteria;
            lastLimit = limit;
            return expiryRows.stream().limit(limit).toList();
        }

        @Override
        public List<ProductStockAlert> findProductStockAlerts(
                LocalDate asOfDate, InventoryAlertCriteria criteria, int limit) {
            dates.add(asOfDate);
            this.criteria = criteria;
            lastLimit = limit;
            return stockRows.stream().limit(limit).toList();
        }
    }
}
