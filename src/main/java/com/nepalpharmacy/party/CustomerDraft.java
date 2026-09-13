package com.nepalpharmacy.party;

public record CustomerDraft(
        String name,
        String phone,
        String address,
        boolean active
) {

    public CustomerDraft normalized() {
        return new CustomerDraft(required(name), optional(phone), optional(address), active);
    }

    private static String required(String value) {
        return value == null ? null : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
