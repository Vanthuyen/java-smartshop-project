package com.example.smartshop.commons.exceptions;

public class InvalidQuantityException extends RuntimeException {
    private final int quantity;

    public InvalidQuantityException(int quantity) {
        super("Invalid quantity: " + quantity + ". Quantity must be positive.");
        this.quantity = quantity;
    }

    public int getQuantity() {
        return quantity;
    }
}
