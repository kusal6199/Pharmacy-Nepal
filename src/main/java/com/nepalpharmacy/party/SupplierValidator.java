package com.nepalpharmacy.party;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupplierValidator {

    private SupplierValidator() {
    }

    public static void validate(SupplierDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("supplier", "Supplier details are required.");
            throw new SupplierValidationException(errors);
        }
        requiredLength("name", "Supplier name", draft.name(), 160, errors);
        optionalLength("phone", "Phone", draft.phone(), 40, errors);
        optionalLength("address", "Address", draft.address(), 240, errors);
        optionalLength("pan", "PAN", draft.pan(), 40, errors);
        if (!errors.isEmpty()) {
            throw new SupplierValidationException(errors);
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
