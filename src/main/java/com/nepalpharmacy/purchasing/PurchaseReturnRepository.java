package com.nepalpharmacy.purchasing;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface PurchaseReturnRepository {

    long nextReturnNumber(TransactionContext transaction);

    void insert(TransactionContext transaction, PurchaseReturn purchaseReturn);

    long count();
}
