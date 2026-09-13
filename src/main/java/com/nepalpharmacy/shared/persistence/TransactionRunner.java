package com.nepalpharmacy.shared.persistence;

public interface TransactionRunner {

    <T> T inTransaction(TransactionWork<T> work);
}
