package com.nepalpharmacy.inventory;

import java.util.List;

public record BoundedAlertResult<T>(List<T> rows, boolean truncated) {
    public BoundedAlertResult {
        rows = List.copyOf(rows);
    }
}
