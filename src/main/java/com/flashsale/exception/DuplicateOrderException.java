package com.flashsale.exception;

public class DuplicateOrderException extends FlashSaleException {
    public DuplicateOrderException() {
        super("User has already purchased this product", "DUPLICATE_ORDER");
    }
}
