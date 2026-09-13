package com.nepalpharmacy.purchasing;

import java.time.Instant;
import java.util.List;

public interface PurchaseEntryRepository {

    Purchase save(PurchaseDraft draft, Instant createdAt);

    List<RecentPurchase> findRecent(int limit);
}
