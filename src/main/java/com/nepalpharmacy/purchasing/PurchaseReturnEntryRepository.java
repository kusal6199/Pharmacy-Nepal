package com.nepalpharmacy.purchasing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReturnEntryRepository {

    List<RecentPurchase> findRecent(int limit);

    Optional<PurchaseReturnSource> findSource(UUID purchaseId);

    PurchaseReturn save(PurchaseReturnDraft draft, Instant createdAt);
}
