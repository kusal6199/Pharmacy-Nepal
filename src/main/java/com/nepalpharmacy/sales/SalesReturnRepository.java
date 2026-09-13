package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface SalesReturnRepository {

    long nextReturnNumber(TransactionContext transaction);

    void insert(TransactionContext transaction, SalesReturn salesReturn);

    long count();
}
