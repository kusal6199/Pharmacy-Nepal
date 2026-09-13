package com.nepalpharmacy.product;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.product.infrastructure.JdbcProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {

    @TempDir
    Path temporaryDirectory;

    private ProductService service;

    @BeforeEach
    void setUp() {
        DatabaseBootstrap database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        JdbcProductRepository repository = new JdbcProductRepository(database::openConnection);
        Clock clock = Clock.fixed(Instant.parse("2026-09-13T04:00:00Z"), ZoneOffset.UTC);
        service = new ProductService(repository, clock, java.util.UUID::randomUUID);
    }

    @Test
    void trimsInputAndStoresIsoTimestamps() {
        Product product = service.create(draft("  Paracetamol 500mg  ", "  Acme Pharma  ", true));

        assertEquals("Paracetamol 500mg", product.name());
        assertEquals("Acme Pharma", product.manufacturer());
        assertEquals(Instant.parse("2026-09-13T04:00:00Z"), product.createdAt());
        assertEquals(product.createdAt(), product.updatedAt());
    }

    @Test
    void rejectsDuplicateActiveNameAndManufacturerButAllowsInactiveDuplicate() {
        service.create(draft("Paracetamol 500mg", "Acme Pharma", true));

        assertThrows(
                ProductValidationException.class,
                () -> service.create(draft("paracetamol 500MG", "ACME PHARMA", true))
        );

        assertDoesNotThrow(() -> service.create(draft("Paracetamol 500mg", "Acme Pharma", false)));
    }

    private ProductDraft draft(String name, String manufacturer, boolean active) {
        return new ProductDraft(
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
                active
        );
    }
}

