package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.PurchaseMultiRequest;
import com.example.smartshop.models.dtos.requets.PurchaseRequest;
import com.example.smartshop.models.dtos.requets.RestockRequest;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.services.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventories")
@Slf4j
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @PostMapping("/restock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restock product", description = "Add stock to a product (Admin only)")
    public ResponseEntity<ApiResponse<Object>> restock(@Valid @RequestBody RestockRequest req) {
        log.info("Restock request received: productId={}, quantity={}",
                req.getProductId(), req.getQuantity());

        inventoryService.restock(
                req.getProductId(),
                req.getQuantity(),
                req.getOperatorId()
        );

        return ResponseUtil.success("Product restocked successfully", null);
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Purchase product", description = "Purchase a single product")
    public ResponseEntity<ApiResponse<Object>> purchase(@Valid @RequestBody PurchaseRequest req) {
        log.info("Purchase request received: productId={}, quantity={}, orderId={}",
                req.getProductId(), req.getQuantity(), req.getOrderId());

        inventoryService.purchase(
                req.getProductId(),
                req.getQuantity(),
                req.getOrderId(),
                req.getCustomerId()
        );

        return ResponseUtil.success("Product purchased successfully", null);
    }

    @PostMapping("/purchase-multi")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Purchase multiple products", description = "Purchase multiple products in one order")
    public ResponseEntity<ApiResponse<Object>> purchaseMultiple(@Valid @RequestBody PurchaseMultiRequest req) {
        log.info("Multiple purchase request received: {} items, orderId={}",
                req.getItems().size(), req.getOrderId());

        inventoryService.purchaseMultiple(
                req.getItems(),
                req.getOrderId(),
                req.getCustomerId()
        );

        return ResponseUtil.success("Order placed successfully", null);
    }
}
