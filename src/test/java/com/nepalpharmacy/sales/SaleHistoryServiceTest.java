package com.nepalpharmacy.sales;

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

class SaleHistoryServiceTest {

    @Test
    void formattedAndPlainInvoiceNumbersResolveToTheSameNumericCriterion() {
        RecordingRepository repository = new RecordingRepository();
        SaleHistoryService service = new SaleHistoryService(repository);

        service.search("2", null, null, null, null);
        assertEquals(2L, repository.lastCriteria.invoiceNumber());

        service.search("000002", null, null, null, null);
        assertEquals(2L, repository.lastCriteria.invoiceNumber());
        assertEquals(SaleHistoryService.SEARCH_LIMIT + 1, repository.lastLimit);
    }

    @Test
    void invalidInvoiceAndReversedDatesAreRejectedBeforeQuerying() {
        RecordingRepository repository = new RecordingRepository();
        SaleHistoryService service = new SaleHistoryService(repository);

        SaleHistoryValidationException exception = assertThrows(
                SaleHistoryValidationException.class,
                () -> service.search("zero", LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 13), null, null));

        assertTrue(exception.fieldErrors().containsKey("invoiceNumber"));
        assertTrue(exception.fieldErrors().containsKey("dateRange"));
        assertEquals(0, repository.calls);
    }

    @Test
    void recentAndSearchResultsUseTheirDocumentedCaps() {
        RecordingRepository repository = new RecordingRepository();
        SaleHistoryService service = new SaleHistoryService(repository);
        repository.rows = summaries(101);

        SaleHistoryResult recent = service.findRecent();
        assertEquals(50, recent.sales().size());
        assertTrue(recent.truncated());
        assertEquals(51, repository.lastLimit);

        SaleHistoryResult searched = service.search(
                null, null, null, "  Alice  ", PaymentMethod.CASH);
        assertEquals(100, searched.sales().size());
        assertTrue(searched.truncated());
        assertEquals("Alice", repository.lastCriteria.customerName());
        assertEquals(PaymentMethod.CASH, repository.lastCriteria.paymentMethod());
        assertEquals(101, repository.lastLimit);

        repository.rows = List.of();
        assertFalse(service.findRecent().truncated());
    }

    private static List<SaleSummary> summaries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new SaleSummary(UUID.randomUUID(), index + 1L,
                        LocalDate.of(2026, 9, 13), "Walk-in", PaymentMethod.CASH,
                        100, SaleReturnStatus.NONE))
                .toList();
    }

    private static final class RecordingRepository implements SaleHistoryRepository {
        private List<SaleSummary> rows = List.of();
        private SaleSearchCriteria lastCriteria;
        private int lastLimit;
        private int calls;

        @Override
        public List<SaleSummary> search(SaleSearchCriteria criteria, int limit) {
            calls++;
            lastCriteria = criteria;
            lastLimit = limit;
            return rows.stream().limit(limit).toList();
        }

        @Override
        public Optional<SaleDetail> findDetail(UUID saleId) {
            return Optional.empty();
        }
    }
}
