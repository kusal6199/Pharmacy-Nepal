package com.nepalpharmacy.product;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProductValidator {

    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_TEXT_LENGTH = 160;

    private ProductValidator() {
    }

    public static void validate(ProductDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (draft == null) {
            errors.put("product", "Product details are required.");
            throw new ProductValidationException(errors);
        }

        if (draft.name() == null || draft.name().isBlank()) {
            errors.put("name", "Name is required.");
        } else if (draft.name().trim().length() > MAX_NAME_LENGTH) {
            errors.put("name", "Name must be at most " + MAX_NAME_LENGTH + " characters.");
        }

        validateOptionalLength("genericName", "Generic name", draft.genericName(), errors);
        validateOptionalLength("manufacturer", "Manufacturer", draft.manufacturer(), errors);

        if (draft.category() == null) {
            errors.put("category", "Category is required.");
        }
        if (draft.unitOfSale() == null) {
            errors.put("unitOfSale", "Unit of sale is required.");
        }
        if (draft.packSize() != null && draft.packSize() <= 0) {
            errors.put("packSize", "Pack size must be greater than 0 when provided.");
        }
        if (draft.purchasePricePaisa() < 0) {
            errors.put("purchasePricePaisa", "Purchase price cannot be negative.");
        }
        if (draft.salePricePaisa() <= 0) {
            errors.put("salePricePaisa", "Sale price must be greater than 0.");
        }
        if (draft.mrpPaisa() != null && draft.mrpPaisa() < 0) {
            errors.put("mrpPaisa", "MRP cannot be negative.");
        }
        if (draft.taxRateBasisPoints() < 0 || draft.taxRateBasisPoints() > 10000) {
            errors.put("taxRateBasisPoints", "Tax rate must be between 0% and 100%.");
        }
        if (draft.reorderThresholdBaseUnits() < 0) {
            errors.put("reorderThresholdBaseUnits", "Reorder threshold cannot be negative.");
        }

        if (!errors.isEmpty()) {
            throw new ProductValidationException(errors);
        }
    }

    private static void validateOptionalLength(
            String field,
            String label,
            String value,
            Map<String, String> errors
    ) {
        if (value != null && value.trim().length() > MAX_TEXT_LENGTH) {
            errors.put(field, label + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
    }
}

