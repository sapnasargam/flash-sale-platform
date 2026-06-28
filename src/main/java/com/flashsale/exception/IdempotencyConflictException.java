package com.flashsale.exception;

public class IdempotencyConflictException extends FlashSaleException {
    public IdempotencyConflictException() {
        super("Idempotency key used with different payload", "IDEMPOTENCY_CONFLICT");
    }
}
