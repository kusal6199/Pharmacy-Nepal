package com.nepalpharmacy.shared.persistence;

@FunctionalInterface
public interface TransactionWork<T> {

    T execute(TransactionContext context);
}
