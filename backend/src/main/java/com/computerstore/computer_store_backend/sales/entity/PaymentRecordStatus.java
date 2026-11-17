package com.computerstore.computer_store_backend.sales.entity;

/** Stored state of an individual payment record (distinct from derived order payment status). */
public enum PaymentRecordStatus {
    RECORDED,
    VOIDED
}
