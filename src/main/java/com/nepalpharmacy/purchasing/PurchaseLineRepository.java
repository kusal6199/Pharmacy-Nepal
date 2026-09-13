package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface PurchaseLineRepository {

    void insert(TransactionContext transaction, PurchaseLine line);

    long count();
}
