package com.nepalpharmacy.purchasing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class PurchaseReturnValidationException extends IllegalArgumentException {

    private final Map<String, String> fieldErrors;

    public PurchaseReturnValidationException(Map<String, String> fieldErrors) {
        super(fieldErrors.values().stream().collect(Collectors.joining(" ")));
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
