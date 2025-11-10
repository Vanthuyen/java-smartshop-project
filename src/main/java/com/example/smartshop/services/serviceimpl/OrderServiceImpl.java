package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.OperationType;
import com.example.smartshop.commons.enums.StatusOrder;
import com.example.smartshop.commons.exceptions.InsufficientStockException;
import com.example.smartshop.commons.exceptions.ResourceNotFoundException;
import com.example.smartshop.commons.exceptions.UnauthorizedException;
import com.example.smartshop.entities.*;
import com.example.smartshop.models.dtos.requets.CreateOrderRequest;
import com.example.smartshop.models.dtos.requets.OrderItemRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.OrderItemResponse;
import com.example.smartshop.models.dtos.responses.OrderResponse;
import com.example.smartshop.repositories.*;
import com.example.smartshop.services.OrderService;
import com.example.smartshop.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

/**
 * Order Service Implementation with Redis Cache
 *
 * Cache Strategy:
 * - Order detail (5 min): Moderate TTL
 * - Orders list (3 min): Short TTL (new orders frequently)
 * - When creating order: evict products, inventory-log, orders caches
 *
 * @version 2.0
 */
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

    /**
     * Create new order
     *
     * Cache eviction:
     * - Clear products cache (stock changed)
     * - Clear inventory-log cache (new logs created)
     * - Clear orders cache (new order added)
     * - Clear product/productStock for affected products
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "inventory-log", allEntries = true),
            @CacheEvict(value = "orders", allEntries = true)
            // Note: Can't evict specific product keys as we have multiple IDs
    })
    public OrderResponse createOrder(CreateOrderRequest request, String userEmail) {
        log.info("🛍️ Creating order for user: {}", userEmail);

        // 1. Find user
        UserEntity user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Validate and lock products (IMPORTANT: Sort to avoid deadlock)
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .sorted()  // ← CRITICAL: Prevent deadlock
                .toList();

        List<ProductEntity> products = productRepository
                .findAllByIdInAndDeletedAtIsNullForUpdate(productIds);

        if (products.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }

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
        List<InventoryLogEntity> inventoryLogs = new ArrayList<>();

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

            int stockBefore = product.getStock();
            product.setStock(stockBefore - itemRequest.getQuantity());

            // Calculate subtotal
            BigDecimal itemPrice = product.getPrice();
            BigDecimal subtotal = itemPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );
            totalPrice = totalPrice.add(subtotal);

            // Create order item
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(itemPrice);
            order.getItems().add(orderItem);

            // Prepare inventory log (will save after order is saved)
            InventoryLogEntity inventoryLog = InventoryLogEntity.builder()
                    .product(product)
                    .quantityChange(-itemRequest.getQuantity())
                    .stockBefore(stockBefore)
                    .stockAfter(product.getStock())
                    .operation(OperationType.PURCHASE)
                    .performedBy(user)
                    .order(order)  // Will have ID after save
                    .build();

            inventoryLogs.add(inventoryLog);

            // Update Redis stock cache
            redisService.updateStock(product.getId(), product.getStock());

            log.debug("✅ Processed order item - Product: {}, Quantity: {}, New Stock: {}",
                    product.getName(), itemRequest.getQuantity(), product.getStock());
        }

        // 5. Set total price and save order
        order.setTotalPrice(totalPrice);
        OrderEntity savedOrder = orderRepository.save(order);

        // 6. Update inventory logs with order reference and save
        for (InventoryLogEntity log : inventoryLogs) {
            log.setNotes("Order #" + savedOrder.getId());
            log.setReferenceCode("ORDER-" + savedOrder.getId());
        }
        inventoryLogRepository.saveAll(inventoryLogs);

        log.info("✅ Order created successfully - OrderId: {}, TotalPrice: {}, Items: {}",
                savedOrder.getId(), totalPrice, savedOrder.getItems().size());
        log.debug("🗑️ Evicted cache: products (all), inventory-log (all), orders (all)");

        return mapToOrderResponse(savedOrder);
    }

    /**
     * Get order by ID
     *
     * Cache key: order ID
     * TTL: 5 minutes
     *
     * Note: Only cache orders for the requesting user (security)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "order",
            key = "#orderId + '-' + #userEmail",
            unless = "#result == null"
    )
    public OrderResponse getOrderById(Long orderId, String userEmail) {
        log.debug("📦 Fetching order from DB: orderId={}, user={}", orderId, userEmail);

        OrderEntity order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Security check: user can only access their own orders
        if (!order.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("You don't have permission to access this order");
        }

        return mapToOrderResponse(order);
    }

    /**
     * Get orders by user
     *
     * Cache key: userEmail-page-size
     * TTL: 3 minutes (new orders created frequently)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "orders",
            key = "#userEmail + '-' + #page + '-' + #size",
            unless = "#result == null || #result.isEmpty()"
    )
    public CacheablePage<OrderResponse> getOrdersByUser(String userEmail, int page, int size) {
        log.debug("📦 Fetching orders from DB: user={}, page={}, size={}", userEmail, page, size);

        UserEntity user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderEntity> orders = orderRepository.findByUserAndDeletedAtIsNull(user, pageable);

        return CacheablePage.of(orders.map(this::mapToOrderResponse));
    }

    /**
     * Map order entity to response DTO
     */
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