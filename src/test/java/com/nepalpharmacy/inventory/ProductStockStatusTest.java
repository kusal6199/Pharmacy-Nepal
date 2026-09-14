package com.nepalpharmacy.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductStockStatusTest {

    @Test
    void noSellableStockIsOutRegardlessOfThreshold() {
        assertEquals(ProductStockStatus.OUT_OF_STOCK,
                ProductStockStatus.classify(0, 0));
        assertEquals(ProductStockStatus.OUT_OF_STOCK,
                ProductStockStatus.classify(0, 50));
        assertEquals(ProductStockStatus.OUT_OF_STOCK,
                ProductStockStatus.classify(-1, 0));
    }

    @Test
    void positiveStockUsesTheConfiguredThresholdInclusively() {
        assertEquals(ProductStockStatus.LOW_STOCK,
                ProductStockStatus.classify(5, 5));
        assertEquals(ProductStockStatus.OK,
                ProductStockStatus.classify(6, 5));
        assertEquals(ProductStockStatus.OK,
                ProductStockStatus.classify(1, 0));
    }
}
