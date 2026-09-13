package com.nepalpharmacy.party;

public record SupplierDraft(
        String name,
        String phone,
        String address,
        String pan,
        boolean active
) {

    public SupplierDraft normalized() {
        return new SupplierDraft(
                required(name), optional(phone), optional(address), optional(pan), active);
    }

    private static String required(String value) {
        return value == null ? null : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
