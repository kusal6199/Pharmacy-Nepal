package com.nepalpharmacy.sales;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleHistoryRepository {
    List<SaleSummary> search(SaleSearchCriteria criteria, int limit);

    Optional<SaleDetail> findDetail(UUID saleId);
}
