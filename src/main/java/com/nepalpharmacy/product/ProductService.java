package com.nepalpharmacy.product;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ProductService {

    private final ProductRepository repository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public ProductService(ProductRepository repository) {
        this(repository, Clock.systemUTC(), UUID::randomUUID);
    }

    public ProductService(ProductRepository repository, Clock clock, Supplier<UUID> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public Product create(ProductDraft input) {
        ProductDraft draft = validateAndNormalize(input, null);
        Instant now = clock.instant();
        Product product = toProduct(idGenerator.get(), draft, now, now);
        repository.insert(product);
        return product;
    }

    public Product update(UUID id, ProductDraft input) {
        Product existing = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        ProductDraft draft = validateAndNormalize(input, id);
        Product updated = toProduct(id, draft, existing.createdAt(), clock.instant());
        repository.update(updated);
        return updated;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    private ProductDraft validateAndNormalize(ProductDraft input, UUID excludedProductId) {
        ProductDraft normalized = input == null ? null : input.normalized();
        ProductValidator.validate(normalized);

        if (normalized.active() && repository.existsActiveWithNameAndManufacturer(
                normalized.name(), normalized.manufacturer(), excludedProductId)) {
            LinkedHashMap<String, String> errors = new LinkedHashMap<>();
            errors.put("name", "An active product with the same name and manufacturer already exists.");
            throw new ProductValidationException(errors);
        }

        return normalized;
    }

    private Product toProduct(UUID id, ProductDraft draft, Instant createdAt, Instant updatedAt) {
        return new Product(
                id,
                draft.name(),
                draft.genericName(),
                draft.manufacturer(),
                draft.category(),
                draft.unitOfSale(),
                draft.packSize(),
                draft.purchasePricePaisa(),
                draft.salePricePaisa(),
                draft.mrpPaisa(),
                draft.taxRateBasisPoints(),
                draft.reorderThresholdBaseUnits(),
                draft.active(),
                createdAt,
                updatedAt
        );
    }
}

