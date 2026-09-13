package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.UUID;

public interface SalesReturnLineRepository {

    void insert(TransactionContext transaction, SalesReturnLine line);

    long returnedQuantityForOriginalLine(
            TransactionContext transaction, UUID originalSaleLineId);

    long count();
}
