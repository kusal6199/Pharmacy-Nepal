package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface SaleLineRepository {

    void insert(TransactionContext transaction, SaleLine line);

    long count();
}
