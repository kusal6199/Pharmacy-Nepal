package com.nepalpharmacy.purchasing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseHistoryServiceTest {

    @Test
    void reversedDatesAreRejectedBeforeTheRepositoryRuns() {
        RecordingRepository repository = new RecordingRepository();
        PurchaseHistoryService service = new PurchaseHistoryService(repository);

        PurchaseHistoryValidationException exception = assertThrows(
                PurchaseHistoryValidationException.class,
                () -> service.search(LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 13), null, null));

        assertTrue(exception.fieldErrors().containsKey("dateRange"));
        assertEquals(0, repository.calls);
    }

    @Test
    void filtersAreTrimmedAndSearchIsCappedAtOneHundredRows() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows = summaries(101);
        PurchaseHistoryService service = new PurchaseHistoryService(repository);

        PurchaseHistoryResult result = service.search(null, null,
                "  Kathmandu  ", "  INV-9  ");

        assertEquals(100, result.purchases().size());
        assertTrue(result.truncated());
        assertEquals("Kathmandu", repository.lastCriteria.supplierName());
        assertEquals("INV-9", repository.lastCriteria.supplierInvoice());
        assertEquals(101, repository.lastLimit);
    }

    @Test
    void defaultHistoryUsesLatestFiftyCapAndEmptyHistoryIsValid() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows = summaries(51);
        PurchaseHistoryService service = new PurchaseHistoryService(repository);

        PurchaseHistoryResult recent = service.findRecent();
        assertEquals(50, recent.purchases().size());
        assertTrue(recent.truncated());
        assertEquals(51, repository.lastLimit);

        repository.rows = List.of();
        assertFalse(service.findRecent().truncated());
    }

    private static List<PurchaseSummary> summaries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new PurchaseSummary(UUID.randomUUID(),
                        LocalDate.of(2026, 9, 13), "Supplier", "INV-" + index, 100))
                .toList();
    }

    private static final class RecordingRepository implements PurchaseHistoryRepository {
        private List<PurchaseSummary> rows = List.of();
        private PurchaseSearchCriteria lastCriteria;
        private int lastLimit;
        private int calls;

        @Override
        public List<PurchaseSummary> search(PurchaseSearchCriteria criteria, int limit) {
            calls++;
            lastCriteria = criteria;
            lastLimit = limit;
            return rows.stream().limit(limit).toList();
        }

        @Override
        public Optional<PurchaseDetail> findDetail(UUID purchaseId) {
            return Optional.empty();
        }
    }
}
