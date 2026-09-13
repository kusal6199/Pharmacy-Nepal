package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PurchaseHistoryService {
    public static final int RECENT_LIMIT = 50;
    public static final int SEARCH_LIMIT = 100;

    private final PurchaseHistoryRepository repository;

    public PurchaseHistoryService(PurchaseHistoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public PurchaseHistoryResult findRecent() {
        return limited(repository.search(
                new PurchaseSearchCriteria(null, null, null, null), RECENT_LIMIT + 1),
                RECENT_LIMIT);
    }

    public PurchaseHistoryResult search(
            LocalDate fromDate,
            LocalDate toDate,
            String supplierName,
            String supplierInvoice
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LinkedHashMap<String, String> errors = new LinkedHashMap<>();
            errors.put("dateRange", "From date must be on or before To date.");
            throw new PurchaseHistoryValidationException(errors);
        }
        PurchaseSearchCriteria criteria = new PurchaseSearchCriteria(
                fromDate,
                toDate,
                normalize(supplierName),
                normalize(supplierInvoice));
        return limited(repository.search(criteria, SEARCH_LIMIT + 1), SEARCH_LIMIT);
    }

    public Optional<PurchaseDetail> findDetail(UUID purchaseId) {
        return repository.findDetail(Objects.requireNonNull(purchaseId, "purchaseId"));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PurchaseHistoryResult limited(List<PurchaseSummary> rows, int limit) {
        boolean truncated = rows.size() > limit;
        return new PurchaseHistoryResult(
                truncated ? rows.subList(0, limit) : rows,
                truncated);
    }
}
