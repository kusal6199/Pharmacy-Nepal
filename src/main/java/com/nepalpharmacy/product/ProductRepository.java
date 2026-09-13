package com.nepalpharmacy.product;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    void insert(Product product);

    void update(Product product);

    Optional<Product> findById(UUID id);

    Optional<Product> findById(TransactionContext transaction, UUID id);

    List<Product> findAll();

    List<Product> searchActiveByName(String query, int limit);

    boolean existsActiveWithNameAndManufacturer(
            String name,
            String manufacturer,
            UUID excludedProductId
    );
}
