package com.nepalpharmacy.credit;

import java.util.List;

public record BoundedAccountResult<T>(List<T> accounts, boolean truncated) {
    public BoundedAccountResult {
        accounts = List.copyOf(accounts);
    }
}
