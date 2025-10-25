package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.OperationType;
import com.example.smartshop.commons.enums.StatusOrder;
import com.example.smartshop.commons.exceptions.InsufficientStockException;
import com.example.smartshop.commons.exceptions.ResourceNotFoundException;
import com.example.smartshop.commons.exceptions.UnauthorizedException;
import com.example.smartshop.entities.*;
import com.example.smartshop.models.dtos.requets.CreateOrderRequest;
import com.example.smartshop.models.dtos.requets.OrderItemRequest;
import com.example.smartshop.models.dtos.responses.OrderItemResponse;
import com.example.smartshop.models.dtos.responses.OrderResponse;
import com.example.smartshop.repositories.*;
import com.example.smartshop.services.OrderService;
import com.example.smartshop.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InventoryLogRepository inventoryLogRepository;
    @Autowired
    private RedisService redisService;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userEmail) {
        log.info("Creating order for user: {}", userEmail);

        // 1. Find user
        UserEntity user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Validate and lock products
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .toList();

        // Lock products for update to prevent concurrent modifications
        List<ProductEntity> products = productRepository.findAllByIdInAndDeletedAtIsNullForUpdate(productIds);

        if (products.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }

        // Create map for easy lookup
        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));

        // 3. Create order entity
        OrderEntity order = OrderEntity.builder()
                .user(user)
                .status(StatusOrder.PENDING)
                .notes(request.getNotes())
                .totalPrice(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        // 4. Process each order item
        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductEntity product = productMap.get(itemRequest.getProductId());

            // Check stock availability
            if (product.getStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                        product.getId(),
                        itemRequest.getQuantity(),
                        product.getStock()
                );
            }

            // Reduce stock
            product.setStock(product.getStock() - itemRequest.getQuantity());

            // Calculate subtotal
            BigDecimal itemPrice = product.getPrice();
            BigDecimal subtotal = itemPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalPrice = totalPrice.add(subtotal);

            // Create order item
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(itemPrice);

            order.getItems().add(orderItem);

            // Create inventory log
            InventoryLogEntity inventoryLog = new InventoryLogEntity();
            inventoryLog.setProduct(product);
            inventoryLog.setQuantityChange(-itemRequest.getQuantity());
            inventoryLog.setOperation(OperationType.PURCHASE);
            inventoryLogRepository.save(inventoryLog);

            // Update Redis cache
            redisService.updateStock(product.getId(), product.getStock());

            log.info("Processed order item - Product: {}, Quantity: {}, New Stock: {}",
                    product.getName(), itemRequest.getQuantity(), product.getStock());
        }

        order.setTotalPrice(totalPrice);

        // 5. Save order
        OrderEntity savedOrder = orderRepository.save(order);

        log.info("Order created successfully - OrderId: {}, TotalPrice: {}, Items: {}",
                savedOrder.getId(), totalPrice, savedOrder.getItems().size());

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, String userEmail) {
        OrderEntity order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Check if user owns this order
        if (!order.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("You don't have permission to access this order");
        }

        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUser(String userEmail, int page, int size) {
        UserEntity user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderEntity> orders = orderRepository.findByUserAndDeletedAtIsNull(user, pageable);

        return orders.map(this::mapToOrderResponse);
    }

    private OrderResponse mapToOrderResponse(OrderEntity order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .userName(order.getUser().getName())
                .userEmail(order.getUser().getEmail())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
