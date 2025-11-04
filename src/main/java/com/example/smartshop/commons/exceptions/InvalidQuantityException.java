package com.example.smartshop.commons.exceptions;

import lombok.Getter;

@Getter
public class InvalidQuantityException extends RuntimeException {
    private final int quantity;

    public InvalidQuantityException(int quantity) {
        super("Invalid quantity: " + quantity + ". Quantity must be positive.");
        this.quantity = quantity;
    }

}
