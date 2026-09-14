package com.nepalpharmacy.purchasing;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PurchaseReturnValidator {

    private PurchaseReturnValidator() {
    }

    public static void validate(PurchaseReturnDraft draft) {
        Map<String, String> errors = basicErrors(draft);
        if (!errors.isEmpty()) {
            throw new PurchaseReturnValidationException(errors);
        }
    }

    public static void validateLine(PurchaseReturnLineDraft line) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateLine(line, 1, errors);
        if (!errors.isEmpty()) {
            throw new PurchaseReturnValidationException(errors);
        }
    }

    public static void validate(
            PurchaseReturnDraft draft,
            Purchase originalPurchase,
            Map<UUID, PurchaseReturnLineAvailability> availabilityByOriginalLine
    ) {
        Map<String, String> errors = basicErrors(draft);
        if (draft != null && originalPurchase != null
                && !originalPurchase.supplierId().equals(draft.supplierId())) {
            errors.put("supplier", "Supplier does not match the original purchase.");
        }

        Map<UUID, Long> requestedByOriginalLine = new LinkedHashMap<>();
        Map<UUID, Long> requestedByBatch = new LinkedHashMap<>();
        if (draft != null && draft.lines() != null) {
            for (int index = 0; index < draft.lines().size(); index++) {
                PurchaseReturnLineDraft line = draft.lines().get(index);
                if (line == null || line.originalPurchaseLineId() == null) {
                    continue;
                }
                PurchaseReturnLineAvailability availability =
                        availabilityByOriginalLine.get(line.originalPurchaseLineId());
                String field = "line" + (index + 1);
                if (availability == null) {
                    errors.put(field + ".originalLine",
                            "Line " + (index + 1) + ": original purchase line does not exist.");
                    continue;
                }
                PurchaseLine original = availability.originalLine();
                if (!original.purchaseId().equals(draft.originalPurchaseId())) {
                    errors.put(field + ".originalLine",
                            "Line " + (index + 1) + ": original line does not belong to this purchase.");
                }
                if (line.batchId() != null && !original.batchId().equals(line.batchId())) {
                    errors.put(field + ".batch",
                            "Line " + (index + 1) + ": batch does not match the original purchase line.");
                }
                if (line.quantityReturnedBaseUnits() > 0) {
                    mergeQuantity(requestedByOriginalLine, line.originalPurchaseLineId(),
                            line.quantityReturnedBaseUnits(), field, errors);
                    mergeQuantity(requestedByBatch, original.batchId(),
                            line.quantityReturnedBaseUnits(), field, errors);
                }
            }
        }
        requestedByOriginalLine.forEach((lineId, requested) -> {
            PurchaseReturnLineAvailability availability = availabilityByOriginalLine.get(lineId);
            if (availability != null && requested > availability.remainingReturnableBaseUnits()) {
                errors.put("remaining." + lineId,
                        "Return quantity " + requested + " exceeds remaining returnable quantity "
                                + Math.max(availability.remainingReturnableBaseUnits(), 0)
                                + " for the original purchase line.");
            }
        });
        requestedByBatch.forEach((batchId, requested) -> {
            long available = availabilityByOriginalLine.values().stream()
                    .filter(item -> item.originalLine().batchId().equals(batchId))
                    .mapToLong(PurchaseReturnLineAvailability::availableBatchQuantityBaseUnits)
                    .findFirst()
                    .orElse(0);
            if (requested > available) {
                errors.put("stock." + batchId,
                        "Purchase return quantity " + requested
                                + " exceeds current available batch stock " + available + ".");
            }
        });
        if (!errors.isEmpty()) {
            throw new PurchaseReturnValidationException(errors);
        }
    }

    public static long totalPaisa(PurchaseReturnDraft draft) {
        return totalPaisa(draft.lines());
    }

    public static long totalPaisa(List<PurchaseReturnLineDraft> lines) {
        try {
            long total = 0;
            for (PurchaseReturnLineDraft line : lines) {
                total = Math.addExact(total, line.lineTotalPaisa());
            }
            return total;
        } catch (ArithmeticException exception) {
            throw error("total", "Purchase return total is too large.");
        }
    }

    private static Map<String, String> basicErrors(PurchaseReturnDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("purchaseReturn", "Purchase return details are required.");
            return errors;
        }
        if (draft.originalPurchaseId() == null) {
            errors.put("originalPurchase", "Original purchase is required.");
        }
        if (draft.supplierId() == null) {
            errors.put("supplier", "Supplier is required.");
        }
        if (draft.returnDate() == null) {
            errors.put("returnDate", "Return date is required.");
        }
        if (draft.reason() == null) {
            errors.put("reason", "Return reason is required.");
        }
        if (draft.settlementMethod() == null) {
            errors.put("settlementMethod", "Settlement method is required.");
        } else if (draft.settlementMethod() == PurchasePaymentMethod.LEGACY_UNSPECIFIED) {
            errors.put("settlementMethod", "Select Cash, QR / digital, or Credit / udharo.");
        }
        if (draft.notes() != null && draft.notes().trim().length() > 500) {
            errors.put("notes", "Notes must be at most 500 characters.");
        }
        if (draft.lines() == null || draft.lines().isEmpty()) {
            errors.put("lines", "Add at least one purchase return line.");
        } else {
            Set<UUID> seen = new HashSet<>();
            for (int index = 0; index < draft.lines().size(); index++) {
                PurchaseReturnLineDraft line = draft.lines().get(index);
                validateLine(line, index + 1, errors);
                if (line != null && line.originalPurchaseLineId() != null
                        && !seen.add(line.originalPurchaseLineId())) {
                    errors.put("line" + (index + 1) + ".duplicate",
                            "Original purchase line can appear only once in a return.");
                }
            }
            if (errors.keySet().stream().noneMatch(key -> key.contains(".quantity")
                    || key.contains(".cost") || key.contains(".total") || key.contains(".line"))) {
                try {
                    totalPaisa(draft);
                } catch (PurchaseReturnValidationException exception) {
                    errors.putAll(exception.fieldErrors());
                }
            }
        }
        return errors;
    }

    private static void validateLine(
            PurchaseReturnLineDraft line, int number, Map<String, String> errors) {
        String field = "line" + number;
        String prefix = "Line " + number + ": ";
        if (line == null) {
            errors.put(field + ".line", prefix + "details are required.");
            return;
        }
        if (line.originalPurchaseLineId() == null) {
            errors.put(field + ".originalLine", prefix + "original purchase line is required.");
        }
        if (line.batchId() == null) {
            errors.put(field + ".batch", prefix + "batch is required.");
        }
        if (line.quantityReturnedBaseUnits() <= 0) {
            errors.put(field + ".quantity", prefix + "quantity must be greater than zero.");
        }
        if (line.unitCostPaisa() < 0) {
            errors.put(field + ".cost", prefix + "original unit cost cannot be negative.");
        }
        try {
            line.lineTotalPaisa();
        } catch (ArithmeticException exception) {
            errors.put(field + ".total", prefix + "line total is too large.");
        }
    }

    private static void mergeQuantity(
            Map<UUID, Long> quantities,
            UUID id,
            int quantity,
            String field,
            Map<String, String> errors
    ) {
        try {
            quantities.merge(id, (long) quantity, Math::addExact);
        } catch (ArithmeticException exception) {
            errors.put(field + ".quantity", "Requested return quantity is too large.");
        }
    }

    private static PurchaseReturnValidationException error(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new PurchaseReturnValidationException(errors);
    }
}
