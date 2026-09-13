package com.nepalpharmacy.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    void insert(Product product);

    void update(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    boolean existsActiveWithNameAndManufacturer(
            String name,
            String manufacturer,
            UUID excludedProductId
    );
}

