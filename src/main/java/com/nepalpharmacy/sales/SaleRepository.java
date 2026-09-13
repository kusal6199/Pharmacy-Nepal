package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface SaleRepository {

    long nextInvoiceNumber(TransactionContext transaction);

    void insert(TransactionContext transaction, Sale sale);

    long count();
}
