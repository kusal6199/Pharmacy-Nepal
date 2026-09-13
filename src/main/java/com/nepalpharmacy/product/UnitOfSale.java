package com.nepalpharmacy.product;

public enum UnitOfSale {
    TABLET("Tablet"),
    CAPSULE("Capsule"),
    MILLILITRE("ML"),
    STRIP("Strip"),
    BOTTLE("Bottle"),
    VIAL("Vial"),
    TUBE("Tube"),
    OTHER("Other");

    private final String displayName;

    UnitOfSale(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

