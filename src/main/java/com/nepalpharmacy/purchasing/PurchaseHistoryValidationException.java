package com.nepalpharmacy.purchasing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PurchaseHistoryValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public PurchaseHistoryValidationException(Map<String, String> fieldErrors) {
        super(String.join(" ", fieldErrors.values()));
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
