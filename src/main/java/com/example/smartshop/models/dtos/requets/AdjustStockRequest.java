package com.example.smartshop.models.dtos.requets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdjustStockRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity change is required")
    private Integer quantityChange; // Có thể âm hoặc dương

    @NotNull(message = "Operator ID is required")
    private Long operatorId;

    @NotBlank(message = "Adjustment reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
