package com.nepalpharmacy.sales;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SalesReturnValidator {

    public static final String UNPAID_SALE_REFUND_MESSAGE =
            "This sale was never paid — refund must reduce the customer's Udharo balance, "
                    + "not be given as cash.";

    private SalesReturnValidator() {
    }

    public static void validate(SalesReturnDraft draft) {
        Map<String, String> errors = basicErrors(draft);
        if (!errors.isEmpty()) {
            throw new SalesReturnValidationException(errors);
        }
    }

    public static void validateLine(SalesReturnLineDraft line) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateLine(line, 1, errors);
        if (!errors.isEmpty()) {
            throw new SalesReturnValidationException(errors);
        }
    }

    public static void validate(
            SalesReturnDraft draft,
            Map<UUID, SalesReturnLineAvailability> availabilityByOriginalLine
    ) {
        validate(draft, null, availabilityByOriginalLine);
    }

    public static void validate(
            SalesReturnDraft draft,
            Sale originalSale,
            Map<UUID, SalesReturnLineAvailability> availabilityByOriginalLine
    ) {
        Map<String, String> errors = basicErrors(draft);
        if (draft != null && draft.refundMethod() != null
                && originalSale != null
                && originalSale.paymentMethod() == PaymentMethod.CREDIT
                && draft.refundMethod() != PaymentMethod.CREDIT) {
            errors.put("refundMethod", UNPAID_SALE_REFUND_MESSAGE);
        }
        if (draft != null && draft.refundMethod() == PaymentMethod.CREDIT
                && originalSale != null && originalSale.customerId() == null) {
            errors.put("refundMethod", "Credit refund requires a customer account.");
        }
        Map<UUID, Long> requestedByOriginalLine = new LinkedHashMap<>();
        if (draft != null && draft.lines() != null) {
            for (int index = 0; index < draft.lines().size(); index++) {
                SalesReturnLineDraft line = draft.lines().get(index);
                if (line == null || line.originalSaleLineId() == null) {
                    continue;
                }
                SalesReturnLineAvailability availability =
                        availabilityByOriginalLine.get(line.originalSaleLineId());
                String field = "line" + (index + 1);
                if (availability == null) {
                    errors.put(field + ".originalLine",
                            "Line " + (index + 1) + ": original sale line does not exist.");
                    continue;
                }
                SaleLine original = availability.originalLine();
                if (!original.saleId().equals(draft.originalSaleId())) {
                    errors.put(field + ".originalLine",
                            "Line " + (index + 1) + ": original line does not belong to this sale.");
                }
                if (line.batchId() != null && !original.batchId().equals(line.batchId())) {
                    errors.put(field + ".batch",
                            "Line " + (index + 1) + ": batch does not match the original sale line.");
                }
                if (line.quantityReturnedBaseUnits() > 0) {
                    mergeQuantity(requestedByOriginalLine, line.originalSaleLineId(),
                            line.quantityReturnedBaseUnits(), field, errors);
                }
            }
        }
        requestedByOriginalLine.forEach((lineId, requested) -> {
            SalesReturnLineAvailability availability = availabilityByOriginalLine.get(lineId);
            if (availability != null && requested > availability.remainingReturnableBaseUnits()) {
                errors.put("remaining." + lineId,
                        "Return quantity " + requested + " exceeds remaining returnable quantity "
                                + Math.max(availability.remainingReturnableBaseUnits(), 0)
                                + " for the original sale line.");
            }
        });
        if (!errors.isEmpty()) {
            throw new SalesReturnValidationException(errors);
        }
    }

    public static List<PaymentMethod> allowedRefundMethods(Sale originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        if (originalSale.paymentMethod() == PaymentMethod.CREDIT) {
            return List.of(PaymentMethod.CREDIT);
        }
        return List.of(PaymentMethod.CASH, PaymentMethod.QR, PaymentMethod.CREDIT);
    }

    public static long totalPaisa(SalesReturnDraft draft) {
        return totalPaisa(draft.lines());
    }

    public static long totalPaisa(List<SalesReturnLineDraft> lines) {
        try {
            long total = 0;
            for (SalesReturnLineDraft line : lines) {
                total = Math.addExact(total, line.lineTotalPaisa());
            }
            if (total <= 0) {
                throw error("total", "Sales return total must be greater than zero.");
            }
            return total;
        } catch (ArithmeticException exception) {
            throw error("total", "Sales return total is too large.");
        }
    }

    private static Map<String, String> basicErrors(SalesReturnDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("salesReturn", "Sales return details are required.");
            return errors;
        }
        if (draft.originalSaleId() == null) {
            errors.put("originalSale", "Original sale is required.");
        }
        if (draft.returnDate() == null) {
            errors.put("returnDate", "Return date is required.");
        }
        if (draft.reason() == null) {
            errors.put("reason", "Return reason is required.");
        }
        if (draft.refundMethod() == null) {
            errors.put("refundMethod", "Refund method is required.");
        }
        if (draft.notes() != null && draft.notes().trim().length() > 500) {
            errors.put("notes", "Notes must be at most 500 characters.");
        }
        if (draft.lines() == null || draft.lines().isEmpty()) {
            errors.put("lines", "Add at least one sales return line.");
        } else {
            Set<UUID> seen = new HashSet<>();
            for (int index = 0; index < draft.lines().size(); index++) {
                SalesReturnLineDraft line = draft.lines().get(index);
                validateLine(line, index + 1, errors);
                if (line != null && line.originalSaleLineId() != null
                        && !seen.add(line.originalSaleLineId())) {
                    errors.put("line" + (index + 1) + ".duplicate",
                            "Original sale line can appear only once in a return.");
                }
            }
            if (errors.keySet().stream().noneMatch(key -> key.contains(".quantity")
                    || key.contains(".price") || key.contains(".total") || key.contains(".line"))) {
                try {
                    totalPaisa(draft);
                } catch (SalesReturnValidationException exception) {
                    errors.putAll(exception.fieldErrors());
                }
            }
        }
        return errors;
    }

    private static void validateLine(
            SalesReturnLineDraft line, int number, Map<String, String> errors) {
        String field = "line" + number;
        String prefix = "Line " + number + ": ";
        if (line == null) {
            errors.put(field + ".line", prefix + "details are required.");
            return;
        }
        if (line.originalSaleLineId() == null) {
            errors.put(field + ".originalLine", prefix + "original sale line is required.");
        }
        if (line.batchId() == null) {
            errors.put(field + ".batch", prefix + "batch is required.");
        }
        if (line.quantityReturnedBaseUnits() <= 0) {
            errors.put(field + ".quantity", prefix + "quantity must be greater than zero.");
        }
        if (line.unitPricePaisa() <= 0) {
            errors.put(field + ".price", prefix + "original unit price must be greater than zero.");
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

    private static SalesReturnValidationException error(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new SalesReturnValidationException(errors);
    }
}
