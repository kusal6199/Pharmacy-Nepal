package com.nepalpharmacy.party;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    void insert(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findById(TransactionContext transaction, UUID id);

    List<Customer> findAllActive();
}
