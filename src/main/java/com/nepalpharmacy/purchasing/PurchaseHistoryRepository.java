package com.nepalpharmacy.purchasing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseHistoryRepository {
    List<PurchaseSummary> search(PurchaseSearchCriteria criteria, int limit);

    Optional<PurchaseDetail> findDetail(UUID purchaseId);
}
