package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.CreateOrderRequest;
import com.example.smartshop.models.dtos.responses.OrderResponse;
import org.springframework.data.domain.Page;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, String userEmail);
    OrderResponse getOrderById(Long orderId, String userEmail);
    Page<OrderResponse> getOrdersByUser(String userEmail, int page, int size);
}
