package com.example.smartshop.services;

public interface RedisService {
    void addToBlacklist(String token, long expirationMillis);
    boolean isBlacklisted(String token);
    Integer getStock(Long productId);
    void updateStock(Long productId, Integer stock);
    void evictStock(Long productId);
}
