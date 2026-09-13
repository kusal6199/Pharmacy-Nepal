package com.nepalpharmacy.party;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    void insert(Supplier supplier);

    Optional<Supplier> findById(UUID id);

    Optional<Supplier> findById(TransactionContext transaction, UUID id);

    List<Supplier> findAllActive();
}
