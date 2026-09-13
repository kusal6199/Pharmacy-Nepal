package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SaleHistoryService {
    public static final int RECENT_LIMIT = 50;
    public static final int SEARCH_LIMIT = 100;

    private final SaleHistoryRepository repository;

    public SaleHistoryService(SaleHistoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public SaleHistoryResult findRecent() {
        return limited(repository.search(
                new SaleSearchCriteria(null, null, null, null, null), RECENT_LIMIT + 1),
                RECENT_LIMIT);
    }

    public SaleHistoryResult search(
            String invoiceText,
            LocalDate fromDate,
            LocalDate toDate,
            String customerName,
            PaymentMethod paymentMethod
    ) {
        SaleSearchCriteria criteria = criteria(
                invoiceText, fromDate, toDate, customerName, paymentMethod);
        return limited(repository.search(criteria, SEARCH_LIMIT + 1), SEARCH_LIMIT);
    }

    public Optional<SaleDetail> findDetail(UUID saleId) {
        return repository.findDetail(Objects.requireNonNull(saleId, "saleId"));
    }

    private static SaleSearchCriteria criteria(
            String invoiceText,
            LocalDate fromDate,
            LocalDate toDate,
            String customerName,
            PaymentMethod paymentMethod
    ) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        Long invoiceNumber = parseInvoice(invoiceText, errors);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            errors.put("dateRange", "From date must be on or before To date.");
        }
        if (!errors.isEmpty()) {
            throw new SaleHistoryValidationException(errors);
        }
        String normalizedCustomer = customerName == null || customerName.isBlank()
                ? null : customerName.trim();
        return new SaleSearchCriteria(
                invoiceNumber, fromDate, toDate, normalizedCustomer, paymentMethod);
    }

    private static Long parseInvoice(
            String invoiceText, LinkedHashMap<String, String> errors) {
        if (invoiceText == null || invoiceText.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(invoiceText.trim());
            if (value <= 0) {
                errors.put("invoiceNumber", "Invoice number must be a positive integer.");
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            errors.put("invoiceNumber", "Invoice number must be a positive integer.");
            return null;
        }
    }

    private static SaleHistoryResult limited(List<SaleSummary> rows, int limit) {
        boolean truncated = rows.size() > limit;
        return new SaleHistoryResult(
                truncated ? rows.subList(0, limit) : rows,
                truncated);
    }
}
