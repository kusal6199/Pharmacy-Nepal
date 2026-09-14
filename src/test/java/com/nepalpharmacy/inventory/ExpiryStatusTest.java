package com.nepalpharmacy.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpiryStatusTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 13);

    @Test
    void classifiesTodayAndEveryBoundaryWithoutOverlap() {
        assertEquals(ExpiryStatus.EXPIRED, status(-1));
        assertEquals(ExpiryStatus.DAYS_0_TO_30, status(0));
        assertEquals(ExpiryStatus.DAYS_0_TO_30, status(30));
        assertEquals(ExpiryStatus.DAYS_31_TO_60, status(31));
        assertEquals(ExpiryStatus.DAYS_31_TO_60, status(60));
        assertEquals(ExpiryStatus.DAYS_61_TO_90, status(61));
        assertEquals(ExpiryStatus.DAYS_61_TO_90, status(90));
        assertEquals(ExpiryStatus.LATER, status(91));
    }

    private static ExpiryStatus status(long days) {
        return ExpiryStatus.classify(AS_OF, AS_OF.plusDays(days));
    }
}
