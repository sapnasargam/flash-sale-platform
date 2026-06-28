package com.flashsale.exception;

public class ResourceNotFoundException extends FlashSaleException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id, "RESOURCE_NOT_FOUND");
    }
}
