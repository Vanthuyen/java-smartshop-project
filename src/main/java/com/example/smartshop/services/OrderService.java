package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.CreateOrderRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.OrderResponse;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, String userEmail);
    OrderResponse getOrderById(Long orderId, String userEmail);
    CacheablePage<OrderResponse> getOrdersByUser(String userEmail, int page, int size);
}
