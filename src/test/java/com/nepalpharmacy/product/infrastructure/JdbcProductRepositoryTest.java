package com.nepalpharmacy.product.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductCategory;
import com.nepalpharmacy.product.UnitOfSale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProductRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private JdbcProductRepository repository;

    @BeforeEach
    void setUp() {
        DatabaseBootstrap database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        repository = new JdbcProductRepository(database::openConnection);
    }

    @Test
    void insertsListsFindsAndUpdatesAProduct() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-13T04:00:00Z");
        Product product = product(id, "Paracetamol 500mg", "Acme Pharma", true, created, created);

        repository.insert(product);

        assertEquals(product, repository.findById(id).orElseThrow());
        assertEquals(1, repository.findAll().size());
        assertTrue(repository.existsActiveWithNameAndManufacturer(
                " paracetamol 500MG ", "acme pharma", null));
        assertFalse(repository.existsActiveWithNameAndManufacturer(
                product.name(), product.manufacturer(), id));

        Instant updatedAt = Instant.parse("2026-09-13T05:00:00Z");
        Product updated = product(id, "Paracetamol 650mg", "Acme Pharma", false, created, updatedAt);
        repository.update(updated);

        assertEquals(updated, repository.findById(id).orElseThrow());
        assertFalse(repository.existsActiveWithNameAndManufacturer(
                updated.name(), updated.manufacturer(), null));
    }

    private Product product(
            UUID id,
            String name,
            String manufacturer,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Product(
                id,
                name,
                "Paracetamol",
                manufacturer,
                ProductCategory.TABLET,
                UnitOfSale.TABLET,
                10,
                1000,
                1500,
                1800L,
                0,
                20,
                active,
                createdAt,
                updatedAt
        );
    }
}

