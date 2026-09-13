package com.nepalpharmacy.product;

import java.util.UUID;

public final class ProductNotFoundException extends IllegalArgumentException {

    public ProductNotFoundException(UUID id) {
        super("Product does not exist: " + id);
    }
}

