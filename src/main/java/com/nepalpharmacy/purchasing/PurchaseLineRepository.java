package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseLineRepository {

    void insert(TransactionContext transaction, PurchaseLine line);

    Optional<PurchaseLine> findById(TransactionContext transaction, UUID id);

    List<PurchaseLine> findByPurchaseId(TransactionContext transaction, UUID purchaseId);

    Optional<PurchaseLine> findById(UUID id);

    List<PurchaseLine> findByPurchaseId(UUID purchaseId);

    long count();
}
