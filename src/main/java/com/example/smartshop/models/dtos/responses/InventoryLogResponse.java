package com.example.smartshop.models.dtos.responses;

import com.example.smartshop.commons.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryLogResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantityChange;
    private Integer stockBefore;
    private Integer stockAfter;
    private OperationType operation;
    private String operationDisplay;
    private Long performedById;
    private String performedByName;
    private Long orderId;
    private String orderCode;
    private String notes;
    private String referenceCode;
    private LocalDateTime createdAt;
}