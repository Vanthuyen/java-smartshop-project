package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.*;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.InventoryLogResponse;
import com.example.smartshop.services.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventories")
@Slf4j
@Tag(name = "Inventory Log Management", description = "APIs for managing Inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @PostMapping("/restock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restock product", description = "Add stock to a product (Admin only)")
    public ResponseEntity<ApiResponse<Object>> restock(@Valid @RequestBody RestockRequest req) {
        log.info("Restock request received: productId={}, quantity={}",
                req.getProductId(), req.getQuantity());

        inventoryService.restock(req);

        return ResponseUtil.success("Product restocked successfully", null);
    }

    @PostMapping("/purchase")
    @Operation(summary = "Purchase product", description = "Purchase a single product")
    public ResponseEntity<ApiResponse<Object>> purchase(@Valid @RequestBody PurchaseRequest req) {
        log.info("Purchase request received: productId={}, quantity={}, orderId={}",
                req.getProductId(), req.getQuantity(), req.getOrderId());

        inventoryService.purchase(req);

        return ResponseUtil.success("Product purchased successfully", null);
    }

    @PostMapping("/purchase-multi")
    @Operation(summary = "Purchase multiple products", description = "Purchase multiple products in one order")
    public ResponseEntity<ApiResponse<Object>> purchaseMultiple(@Valid @RequestBody PurchaseMultiRequest req) {
        log.info("Multiple purchase request received: {} items, orderId={}",
                req.getItems().size(), req.getOrderId());

        inventoryService.purchaseMultiple(req);

        return ResponseUtil.success("Order placed successfully", null);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all inventory logs", description = "Get paginated inventory logs (Admin only)")
    public ResponseEntity<ApiResponse<CacheablePage<InventoryLogResponse>>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CacheablePage<InventoryLogResponse> logs = inventoryService.getAllLogs(
                PageRequest.of(page, size)
        );

        return ResponseUtil.success("Inventory logs retrieved successfully", logs);
    }

    @GetMapping("/logs/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get logs by product", description = "Get inventory logs for specific product (Admin only)")
    public ResponseEntity<ApiResponse<CacheablePage<InventoryLogResponse>>> getLogsByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CacheablePage<InventoryLogResponse> logs = inventoryService.getLogsByProduct(
                productId,
                PageRequest.of(page, size)
        );

        return ResponseUtil.success("Product logs retrieved successfully", logs);
    }

    @GetMapping("/logs/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get logs by order", description = "Get inventory logs for specific order (Admin only)")
    public ResponseEntity<ApiResponse<List<InventoryLogResponse>>> getLogsByOrder(
            @PathVariable Long orderId) {

        List<InventoryLogResponse> logs = inventoryService.getLogsByOrder(orderId);

        return ResponseUtil.success("Order logs retrieved successfully", logs);
    }

    @GetMapping("/logs/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get logs by user", description = "Get inventory logs performed by specific user")
    public ResponseEntity<ApiResponse<CacheablePage<InventoryLogResponse>>> getLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CacheablePage<InventoryLogResponse> logs = inventoryService.getLogsByUser(
                userId,
                PageRequest.of(page, size)
        );

        return ResponseUtil.success("User logs retrieved successfully", logs);
    }

    @GetMapping("/logs/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get logs by date range")
    public ResponseEntity<ApiResponse<CacheablePage<InventoryLogResponse>>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CacheablePage<InventoryLogResponse> logs = inventoryService.getLogsByDateRange(
                startDate,
                endDate,
                PageRequest.of(page, size)
        );

        return ResponseUtil.success("Date range logs retrieved successfully", logs);
    }

    @PostMapping("/return")
    @Operation(summary = "Return product", description = "Return purchased product and restore stock")
    public ResponseEntity<ApiResponse<Object>> returnProduct(@Valid @RequestBody ReturnRequest req) {
        log.info("Return request: productId={}, quantity={}", req.getProductId(), req.getQuantity());

        inventoryService.returnProduct(req);

        return ResponseUtil.success("Product returned successfully", null);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adjust stock", description = "Manual stock adjustment (Admin only)")
    public ResponseEntity<ApiResponse<Object>> adjustStock(@Valid @RequestBody AdjustStockRequest req) {
        log.info("Adjust stock request: productId={}, change={}",
                req.getProductId(), req.getQuantityChange());

        inventoryService.adjustStock(req);

        return ResponseUtil.success("Stock adjusted successfully", null);
    }
}
