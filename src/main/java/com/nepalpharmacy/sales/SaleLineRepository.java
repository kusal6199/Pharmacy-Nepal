package com.nepalpharmacy.sales;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleLineRepository {

    void insert(TransactionContext transaction, SaleLine line);

    Optional<SaleLine> findById(TransactionContext transaction, UUID id);

    List<SaleLine> findBySaleId(TransactionContext transaction, UUID saleId);

    Optional<SaleLine> findById(UUID id);

    List<SaleLine> findBySaleId(UUID saleId);

    long count();
}
