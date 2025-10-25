package com.example.smartshop.models.dtos.requets;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PurchaseMultiRequest {
    @NotEmpty(message = "Items cannot be empty")
    private Map<Long, Integer> items;

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Customer ID is required")
    private Long customerId;
}

