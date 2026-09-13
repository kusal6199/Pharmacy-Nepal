package com.nepalpharmacy.sales;

import com.nepalpharmacy.inventory.BatchStock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SaleValidator {

    private SaleValidator() {
    }

    public static void validate(SaleDraft draft) {
        Map<String, String> errors = basicErrors(draft);
        if (!errors.isEmpty()) {
            throw new SaleValidationException(errors);
        }
    }

    public static void validateLine(SaleLineDraft line) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateLine(line, 1, errors);
        if (!errors.isEmpty()) {
            throw new SaleValidationException(errors);
        }
    }

    public static void validate(SaleDraft draft, Map<UUID, BatchStock> stockByBatch) {
        Map<String, String> errors = basicErrors(draft);
        if (draft != null && draft.lines() != null && !draft.lines().isEmpty()
                && draft.saleDate() != null) {
            Map<UUID, Long> requestedByBatch = new LinkedHashMap<>();
            for (int index = 0; index < draft.lines().size(); index++) {
                SaleLineDraft line = draft.lines().get(index);
                if (line == null || line.batchId() == null) {
                    continue;
                }
                BatchStock stock = stockByBatch.get(line.batchId());
                if (stock == null) {
                    errors.put("line" + (index + 1) + ".batch", "Line " + (index + 1)
                            + ": selected batch no longer exists.");
                    continue;
                }
                if (stock.batch().expiryDate().isBefore(draft.saleDate())) {
                    errors.put("line" + (index + 1) + ".expiry", "Line " + (index + 1)
                            + ": expired batch cannot be sold.");
                }
                if (line.quantitySoldBaseUnits() > 0) {
                    try {
                        requestedByBatch.merge(
                                line.batchId(),
                                (long) line.quantitySoldBaseUnits(),
                                Math::addExact);
                    } catch (ArithmeticException exception) {
                        errors.put("line" + (index + 1) + ".quantity", "Line " + (index + 1)
                                + ": requested quantity is too large.");
                    }
                }
            }
            requestedByBatch.forEach((batchId, requested) -> {
                BatchStock stock = stockByBatch.get(batchId);
                if (stock != null && requested > stock.quantityBaseUnits()) {
                    errors.put("stock." + batchId, "Insufficient stock for batch "
                            + stock.batch().batchNumber() + ": requested " + requested
                            + ", available " + stock.quantityBaseUnits() + ".");
                }
            });
        }
        if (!errors.isEmpty()) {
            throw new SaleValidationException(errors);
        }
    }

    public static long totalPaisa(SaleDraft draft) {
        return totalPaisa(draft.lines());
    }

    public static long totalPaisa(java.util.List<SaleLineDraft> lines) {
        try {
            long total = 0;
            for (SaleLineDraft line : lines) {
                total = Math.addExact(total, line.lineTotalPaisa());
            }
            if (total <= 0) {
                throw validationError("total", "Sale total must be greater than zero.");
            }
            return total;
        } catch (ArithmeticException exception) {
            throw validationError("total", "Sale total is too large.");
        }
    }

    private static Map<String, String> basicErrors(SaleDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("sale", "Sale details are required.");
            return errors;
        }
        if (draft.saleDate() == null) {
            errors.put("saleDate", "Sale date is required.");
        }
        if (draft.paymentMethod() == null) {
            errors.put("paymentMethod", "Payment method is required.");
        } else if (draft.paymentMethod() == PaymentMethod.CREDIT && draft.customerId() == null) {
            errors.put("customer", "Customer is required for a credit sale.");
        }
        if (draft.lines() == null || draft.lines().isEmpty()) {
            errors.put("lines", "Add at least one sale line.");
        } else {
            for (int index = 0; index < draft.lines().size(); index++) {
                validateLine(draft.lines().get(index), index + 1, errors);
            }
            if (errors.keySet().stream().noneMatch(key -> key.endsWith(".quantity")
                    || key.endsWith(".price") || key.endsWith(".total") || key.endsWith(".line"))) {
                try {
                    totalPaisa(draft);
                } catch (SaleValidationException exception) {
                    errors.putAll(exception.fieldErrors());
                }
            }
        }
        return errors;
    }

    private static void validateLine(
            SaleLineDraft line, int number, Map<String, String> errors) {
        String field = "line" + number;
        String prefix = "Line " + number + ": ";
        if (line == null) {
            errors.put(field + ".line", prefix + "details are required.");
            return;
        }
        if (line.batchId() == null) {
            errors.put(field + ".batch", prefix + "batch is required.");
        }
        if (line.quantitySoldBaseUnits() <= 0) {
            errors.put(field + ".quantity", prefix + "quantity must be greater than zero.");
        }
        if (line.unitSalePricePaisa() <= 0) {
            errors.put(field + ".price", prefix + "unit sale price must be greater than zero.");
        }
        try {
            line.lineTotalPaisa();
        } catch (ArithmeticException exception) {
            errors.put(field + ".total", prefix + "line total is too large.");
        }
    }

    private static SaleValidationException validationError(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new SaleValidationException(errors);
    }
}
