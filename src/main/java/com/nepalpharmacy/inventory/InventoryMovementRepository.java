package com.nepalpharmacy.inventory;

import com.nepalpharmacy.shared.persistence.TransactionContext;

public interface InventoryMovementRepository {

    void insert(TransactionContext transaction, InventoryMovement movement);

    long count();
}
