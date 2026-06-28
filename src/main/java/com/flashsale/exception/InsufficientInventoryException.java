package com.flashsale.exception;

public class InsufficientInventoryException extends FlashSaleException {
    public InsufficientInventoryException() {
        super("Insufficient inventory available", "INSUFFICIENT_INVENTORY");
    }
}
