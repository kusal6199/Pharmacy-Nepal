package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository {

    void insert(TransactionContext transaction, Purchase purchase);

    Optional<Purchase> findById(TransactionContext transaction, UUID id);

    List<RecentPurchase> findRecent(int limit);

    long count();
}
