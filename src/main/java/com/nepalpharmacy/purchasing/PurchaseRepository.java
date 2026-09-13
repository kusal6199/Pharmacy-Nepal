package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;

public interface PurchaseRepository {

    void insert(TransactionContext transaction, Purchase purchase);

    List<RecentPurchase> findRecent(int limit);

    long count();
}
