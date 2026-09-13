package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository {

    long nextInvoiceNumber(TransactionContext transaction);

    void insert(TransactionContext transaction, Sale sale);

    Optional<Sale> findById(TransactionContext transaction, UUID id);

    Optional<Sale> findByInvoiceNumber(TransactionContext transaction, long invoiceNumber);

    long count();
}
