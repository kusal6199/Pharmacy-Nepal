package com.nepalpharmacy.credit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public AccountValidationException(Map<String, String> fieldErrors) {
        super(String.join(" ", fieldErrors.values()));
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
