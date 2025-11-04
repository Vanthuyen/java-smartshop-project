package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.CreateOrderRequest;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.models.dtos.responses.OrderResponse;
import com.example.smartshop.services.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Management", description = "APIs for managing Order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {

        String userEmail = authentication.getName();
        OrderResponse order = orderService.createOrder(request, userEmail);

        return ResponseUtil.created("Order created successfully", order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable Long orderId,
            Authentication authentication) {

        String userEmail = authentication.getName();
        OrderResponse order = orderService.getOrderById(orderId, userEmail);

        return ResponseUtil.success("Order detail retrieved successfully", order);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String userEmail = authentication.getName();
        Page<OrderResponse> orders = orderService.getOrdersByUser(userEmail, page, size);

        return ResponseUtil.success("Orders retrieved successfully", orders);
    }
}
