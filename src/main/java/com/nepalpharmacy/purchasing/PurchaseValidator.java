package com.nepalpharmacy.purchasing;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PurchaseValidator {

    private PurchaseValidator() {
    }

    public static void validate(PurchaseDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("purchase", "Purchase details are required.");
            throw new PurchaseValidationException(errors);
        }
        if (draft.supplierId() == null) {
            errors.put("supplier", "Supplier is required.");
        }
        if (draft.purchaseDate() == null) {
            errors.put("purchaseDate", "Purchase date is required.");
        }
        if (draft.paymentMethod() == null) {
            errors.put("paymentMethod", "Payment method is required.");
        } else if (draft.paymentMethod() == PurchasePaymentMethod.LEGACY_UNSPECIFIED) {
            errors.put("paymentMethod", "Select Cash, QR / digital, or Credit / udharo.");
        }
        if (draft.invoiceNumber() != null && draft.invoiceNumber().trim().length() > 80) {
            errors.put("invoiceNumber", "Invoice number must be at most 80 characters.");
        }
        if (draft.lines() == null || draft.lines().isEmpty()) {
            errors.put("lines", "Add at least one purchase line.");
        } else {
            for (int index = 0; index < draft.lines().size(); index++) {
                validateLine(draft, draft.lines().get(index), index + 1, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new PurchaseValidationException(errors);
        }
    }

    public static long totalPaisa(PurchaseDraft draft) {
        try {
            long total = 0;
            for (PurchaseLineDraft line : draft.lines()) {
                total = Math.addExact(total, line.lineTotalPaisa());
            }
            return total;
        } catch (ArithmeticException exception) {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("total", "Purchase total is too large.");
            throw new PurchaseValidationException(errors);
        }
    }

    private static void validateLine(
            PurchaseDraft draft,
            PurchaseLineDraft line,
            int number,
            Map<String, String> errors
    ) {
        String prefix = "Line " + number + ": ";
        String field = "line" + number;
        if (line == null) {
            errors.put(field, prefix + "details are required.");
            return;
        }
        if (line.productId() == null) {
            errors.put(field + ".product", prefix + "product is required.");
        }
        if (line.batchNumber() == null || line.batchNumber().isBlank()) {
            errors.put(field + ".batchNumber", prefix + "batch number is required.");
        } else if (line.batchNumber().trim().length() > 80) {
            errors.put(field + ".batchNumber", prefix + "batch number must be at most 80 characters.");
        }
        if (line.expiryDate() == null) {
            errors.put(field + ".expiryDate", prefix + "expiry date is required.");
        } else if (draft.purchaseDate() != null && !line.expiryDate().isAfter(draft.purchaseDate())) {
            errors.put(field + ".expiryDate", prefix + "expiry must be after the purchase date.");
        }
        if (line.quantityReceivedBaseUnits() <= 0) {
            errors.put(field + ".quantity", prefix + "quantity must be greater than zero.");
        }
        if (line.unitPurchasePricePaisa() < 0) {
            errors.put(field + ".price", prefix + "unit purchase price cannot be negative.");
        }
        try {
            line.lineTotalPaisa();
        } catch (ArithmeticException exception) {
            errors.put(field + ".total", prefix + "line total is too large.");
        }
    }
}
