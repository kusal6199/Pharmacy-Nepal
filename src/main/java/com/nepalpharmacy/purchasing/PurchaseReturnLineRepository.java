package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.UUID;

public interface PurchaseReturnLineRepository {

    void insert(TransactionContext transaction, PurchaseReturnLine line);

    long returnedQuantityForOriginalLine(
            TransactionContext transaction, UUID originalPurchaseLineId);

    long count();
}
