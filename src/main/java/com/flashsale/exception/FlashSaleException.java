package com.flashsale.exception;

public class FlashSaleException extends RuntimeException {
    private final String errorCode;
    public FlashSaleException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
