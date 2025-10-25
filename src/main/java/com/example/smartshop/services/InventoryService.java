package com.example.smartshop.services;

import java.util.Map;

public interface InventoryService {
    void restock(Long productId, int quantity, Long operatorUserId);
    void purchase(Long productId, int quantity, Long orderId, Long customerId);
    void purchaseMultiple(Map<Long, Integer> items, Long orderId, Long customerId);
}
