package com.flashsale.exception;

public class SaleNotActiveException extends FlashSaleException {
    public SaleNotActiveException() {
        super("Sale is not currently active", "SALE_NOT_ACTIVE");
    }
}
