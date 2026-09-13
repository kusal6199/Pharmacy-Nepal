package com.nepalpharmacy.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductValidatorTest {

    @Test
    void acceptsAValidProductWithOptionalFieldsMissing() {
        assertDoesNotThrow(() -> ProductValidator.validate(validDraft()));
    }

    @Test
    void requiresAReasonablySizedName() {
        ProductValidationException missing = assertThrows(
                ProductValidationException.class,
                () -> ProductValidator.validate(withName("  "))
        );
        assertTrue(missing.fieldErrors().containsKey("name"));

        ProductValidationException tooLong = assertThrows(
                ProductValidationException.class,
                () -> ProductValidator.validate(withName("x".repeat(ProductValidator.MAX_NAME_LENGTH + 1)))
        );
        assertTrue(tooLong.fieldErrors().containsKey("name"));
    }

    @Test
    void requiresAUnitOfSale() {
        ProductDraft draft = new ProductDraft(
                "Paracetamol 500mg", null, null, ProductCategory.TABLET, null, 10,
                1000, 1500, null, 0, 0, true
        );

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> ProductValidator.validate(draft)
        );
        assertTrue(exception.fieldErrors().containsKey("unitOfSale"));
    }

    @Test
    void rejectsZeroOrNegativeSalePrice() {
        ProductDraft zeroPrice = new ProductDraft(
                "Paracetamol 500mg", null, null, ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                1000, 0, null, 0, 0, true
        );

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> ProductValidator.validate(zeroPrice)
        );
        assertEquals("Sale price must be greater than 0.", exception.fieldErrors().get("salePricePaisa"));
    }

    @Test
    void allowsSalePriceBelowPurchasePrice() {
        ProductDraft lossLeader = new ProductDraft(
                "Discounted product", null, null, ProductCategory.OTHER, UnitOfSale.OTHER, null,
                5000, 4000, null, 0, 0, true
        );

        assertDoesNotThrow(() -> ProductValidator.validate(lossLeader));
    }

    @Test
    void rejectsNegativeReorderThreshold() {
        ProductDraft draft = new ProductDraft(
                "Paracetamol 500mg", null, null, ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                1000, 1500, null, 0, -1, true
        );

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> ProductValidator.validate(draft)
        );
        assertTrue(exception.fieldErrors().containsKey("reorderThresholdBaseUnits"));
    }

    private ProductDraft validDraft() {
        return withName("Paracetamol 500mg");
    }

    private ProductDraft withName(String name) {
        return new ProductDraft(
                name, null, null, ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                1000, 1500, null, 0, 0, true
        );
    }
}

