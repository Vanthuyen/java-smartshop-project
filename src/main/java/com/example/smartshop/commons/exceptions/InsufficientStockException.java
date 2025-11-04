package com.example.smartshop.commons.exceptions;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {
    private final Long productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(Long productId, int requested, int available) {
        super(String.format("Insufficient stock for product %d: requested %d, available %d",
                productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

}
