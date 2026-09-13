package com.nepalpharmacy.party;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CustomerValidator {

    private CustomerValidator() {
    }

    public static void validate(CustomerDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("customer", "Customer details are required.");
            throw new CustomerValidationException(errors);
        }
        requiredLength("name", "Customer name", draft.name(), 160, errors);
        optionalLength("phone", "Phone", draft.phone(), 40, errors);
        optionalLength("address", "Address", draft.address(), 240, errors);
        if (!errors.isEmpty()) {
            throw new CustomerValidationException(errors);
        }
    }

    private static void requiredLength(
            String field, String label, String value, int maximum, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            errors.put(field, label + " is required.");
        } else if (value.trim().length() > maximum) {
            errors.put(field, label + " must be at most " + maximum + " characters.");
        }
    }

    private static void optionalLength(
            String field, String label, String value, int maximum, Map<String, String> errors) {
        if (value != null && value.trim().length() > maximum) {
            errors.put(field, label + " must be at most " + maximum + " characters.");
        }
    }
}
