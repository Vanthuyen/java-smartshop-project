package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.PurchaseMultiRequest;
import com.example.smartshop.models.dtos.requets.PurchaseRequest;
import com.example.smartshop.models.dtos.requets.RestockRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.InventoryLogResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryService {
    void restock(RestockRequest request);
    void purchase(PurchaseRequest request);
    void purchaseMultiple(PurchaseMultiRequest request);
    CacheablePage<InventoryLogResponse> getAllLogs(Pageable pageable);
    CacheablePage<InventoryLogResponse> getLogsByProduct(Long productId, Pageable pageable);
    List<InventoryLogResponse> getLogsByOrder(Long orderId);
    CacheablePage<InventoryLogResponse> getLogsByUser(Long userId, Pageable pageable);
    CacheablePage<InventoryLogResponse> getLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
}
