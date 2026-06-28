package com.flashsale.exception;

public class InvalidPaymentException extends FlashSaleException {
    public InvalidPaymentException(String message) {
        super(message, "INVALID_PAYMENT");
    }
}
