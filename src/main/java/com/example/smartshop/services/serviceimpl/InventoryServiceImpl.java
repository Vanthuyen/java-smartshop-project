package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.OperationType;
import com.example.smartshop.commons.exceptions.InvalidQuantityException;
import com.example.smartshop.commons.exceptions.ProductNotFoundException;
import com.example.smartshop.commons.exceptions.ResourceNotFoundException;
import com.example.smartshop.entities.InventoryLogEntity;
import com.example.smartshop.entities.OrderEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.*;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.InventoryLogResponse;
import com.example.smartshop.repositories.InventoryLogRepository;
import com.example.smartshop.repositories.OrderRepository;
import com.example.smartshop.repositories.ProductRepository;
import com.example.smartshop.repositories.UserRepository;
import com.example.smartshop.services.InventoryService;
import com.example.smartshop.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inventory Service Implementation with Redis Cache
 *
 * Cache Strategy:
 * - Inventory logs (2 min): Fresh data needed, changes frequently
 * - When stock changes: evict product, products, productStock, inventory-log caches
 *
 * @version 2.0
 */
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Restock product with cache eviction
     *
     * When restocking:
     * 1. Product detail cache must be cleared (stock changed)
     * 2. Products list cache must be cleared (may affect display)
     * 3. Product stock cache must be cleared
     * 4. Inventory log cache must be cleared (new log added)
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#request.productId"),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productStock", key = "#request.productId"),
            @CacheEvict(value = "inventory-log", allEntries = true)
    })
    public void restock(RestockRequest request) {
        log.info("📦 Restocking product: productId={}, quantity={}, operatorId={}",
                request.getProductId(), request.getQuantity(), request.getOperatorId());

        // Validation
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // Lock and get product
        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));

        UserEntity operator = userRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new RuntimeException("Operator not found"));

        int stockBefore = product.getStock();

        // Update stock
        product.setStock(stockBefore + request.getQuantity());
        productRepository.save(product);

        // Create inventory log
        InventoryLogEntity log = InventoryLogEntity.builder()
                .product(product)
                .quantityChange(request.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(product.getStock())
                .operation(OperationType.RESTOCK)
                .performedBy(operator)
                .notes("Restocked by " + operator.getName())
                .referenceCode("RESTOCK-" + System.currentTimeMillis())
                .build();

        inventoryLogRepository.save(log);

        // Update Redis stock cache
        redisService.updateStock(request.getProductId(), product.getStock());
    }

    /**
     * Purchase product (single item)
     * Same cache eviction strategy as restock
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#request.productId"),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productStock", key = "#request.productId"),
            @CacheEvict(value = "inventory-log", allEntries = true)
    })
    public void purchase(PurchaseRequest request) {
        log.info("🛒 Processing purchase: productId={}, quantity={}, orderId={}, customerId={}",
                request.getProductId(), request.getQuantity(), request.getOrderId(), request.getCustomerId());

        if (request.getQuantity() <= 0) {
            throw new InvalidQuantityException(request.getQuantity());
        }

        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        UserEntity customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        int stockBefore = product.getStock();

        // Check stock availability
        if (stockBefore < request.getQuantity()) {
            throw new RuntimeException(
                    String.format("Insufficient stock for product %s. Available: %d, Requested: %d",
                            product.getName(), stockBefore, request.getQuantity())
            );
        }

        // Reduce stock
        product.setStock(stockBefore - request.getQuantity());
        productRepository.save(product);

        // Create log
        InventoryLogEntity log = InventoryLogEntity.builder()
                .product(product)
                .quantityChange(-request.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(product.getStock())
                .operation(OperationType.PURCHASE)
                .performedBy(customer)
                .notes("Purchased by " + customer.getName())
                .referenceCode("ORDER-" + request.getOrderId())
                .build();

        inventoryLogRepository.save(log);

        // Update Redis
        redisService.updateStock(request.getProductId(), product.getStock());
    }

    /**
     * Purchase multiple products
     * Clear cache for ALL affected products
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "inventory-log", allEntries = true)
            // Note: Can't evict specific product/productStock keys here as we have multiple IDs
            // They will expire via TTL
    })
    public void purchaseMultiple(PurchaseMultiRequest request) {
        log.info("🛒 Processing multiple purchases: {} items, orderId={}, customerId={}",
                request.getItems().size(), request.getOrderId(), request.getCustomerId());

        // Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Items cannot be empty");
        }

        // Validate all quantities
        for (Map.Entry<Long, Integer> entry : request.getItems().entrySet()) {
            if (entry.getValue() <= 0) {
                throw new InvalidQuantityException(entry.getValue());
            }
        }

        UserEntity customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Sort IDs to avoid deadlock
        List<Long> sortedIds = request.getItems().keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        // Lock products
        List<ProductEntity> products = productRepository.findAllByIdInForUpdate(sortedIds);

        if (products.size() != sortedIds.size()) {
            throw new ProductNotFoundException(null);
        }

        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));

        // Validate stock for all items first
        for (Map.Entry<Long, Integer> entry : request.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            ProductEntity product = productMap.get(productId);

            if (product == null) {
                throw new ProductNotFoundException(productId);
            }

            if (product.getStock() < quantity) {
                throw new RuntimeException(
                        String.format("Insufficient stock for product %s. Available: %d, Requested: %d",
                                product.getName(), product.getStock(), quantity)
                );
            }
        }

        // Process all items
        List<InventoryLogEntity> logs = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : request.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            ProductEntity product = productMap.get(productId);

            int stockBefore = product.getStock();
            product.setStock(stockBefore - quantity);

            // Create log
            InventoryLogEntity log = InventoryLogEntity.builder()
                    .product(product)
                    .quantityChange(-quantity)
                    .stockBefore(stockBefore)
                    .stockAfter(product.getStock())
                    .operation(OperationType.PURCHASE)
                    .performedBy(customer)
                    .notes("Multi-purchase by " + customer.getName())
                    .referenceCode("ORDER-" + request.getOrderId())
                    .build();

            logs.add(log);
        }

        // Save all
        productRepository.saveAll(products);
        inventoryLogRepository.saveAll(logs);

        // Update Redis for all products
        for (ProductEntity product : products) {
            redisService.updateStock(product.getId(), product.getStock());
        }

        log.info("✅ Multiple purchases completed - Total items: {}, OrderId: {}",
                request.getItems().size(), request.getOrderId());
        log.debug("🗑️ Evicted cache: products (all), inventory-log (all)");
    }

    /**
     * Return product
     * Same cache eviction as restock (stock increases)
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#request.productId"),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productStock", key = "#request.productId"),
            @CacheEvict(value = "inventory-log", allEntries = true)
    })
    public void returnProduct(ReturnRequest request) {
        log.info("↩️ Processing return: productId={}, quantity={}, orderId={}, customerId={}",
                request.getProductId(), request.getQuantity(), request.getOrderId(), request.getCustomerId());

        if (request.getQuantity() <= 0) {
            throw new InvalidQuantityException(request.getQuantity());
        }

        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        UserEntity customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        OrderEntity order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        int stockBefore = product.getStock();
        product.setStock(stockBefore + request.getQuantity());
        productRepository.save(product);

        InventoryLogEntity log = InventoryLogEntity.builder()
                .product(product)
                .quantityChange(request.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(product.getStock())
                .operation(OperationType.RETURN)
                .performedBy(customer)
                .order(order)
                .notes("Return reason: " + request.getReason())
                .referenceCode("RETURN-ORDER-" + request.getOrderId())
                .build();

        inventoryLogRepository.save(log);
        redisService.updateStock(request.getProductId(), product.getStock());
    }

    /**
     * Adjust stock (manual adjustment)
     * Same cache eviction strategy
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#request.productId"),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productStock", key = "#request.productId"),
            @CacheEvict(value = "inventory-log", allEntries = true)
    })
    public void adjustStock(AdjustStockRequest request) {
        log.info("⚙️ Adjusting stock: productId={}, change={}, operatorId={}, reason={}",
                request.getProductId(), request.getQuantityChange(), request.getOperatorId(), request.getReason());

        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        UserEntity operator = userRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new RuntimeException("Operator not found"));

        int stockBefore = product.getStock();
        int newStock = stockBefore + request.getQuantityChange();

        if (newStock < 0) {
            throw new RuntimeException("Stock cannot be negative after adjustment");
        }

        product.setStock(newStock);
        productRepository.save(product);

        InventoryLogEntity log = InventoryLogEntity.builder()
                .product(product)
                .quantityChange(request.getQuantityChange())
                .stockBefore(stockBefore)
                .stockAfter(newStock)
                .operation(OperationType.ADJUSTMENT)
                .performedBy(operator)
                .notes("Adjustment reason: " + request.getReason())
                .referenceCode("ADJUST-" + System.currentTimeMillis())
                .build();

        inventoryLogRepository.save(log);
        redisService.updateStock(request.getProductId(), newStock);
    }

    /**
     * Get all inventory logs with pagination
     *
     * Cache key: page-size
     * TTL: 2 minutes (logs change frequently)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "'all-' + #pageable.pageNumber + '-' + #pageable.pageSize",
            unless = "#result == null || #result.isEmpty()"
    )
    public CacheablePage<InventoryLogResponse> getAllLogs(Pageable pageable) {
        log.debug("📋 Fetching inventory logs from DB: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<InventoryLogResponse> page = inventoryLogRepository
                .findAllWithDetails(pageable)
                .map(this::mapToResponse);

        return CacheablePage.of(page);
    }

    /**
     * Get logs by product
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "'product-' + #productId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public CacheablePage<InventoryLogResponse> getLogsByProduct(Long productId, Pageable pageable) {
        log.debug("📋 Fetching logs by product: productId={}", productId);

        Page<InventoryLogResponse> page = inventoryLogRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::mapToResponse);

        return CacheablePage.of(page);
    }

    /**
     * Get logs by order
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory-log", key = "'order-' + #orderId")
    public List<InventoryLogResponse> getLogsByOrder(Long orderId) {
        log.debug("📋 Fetching logs by order: orderId={}", orderId);

        return inventoryLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by user
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "'user-' + #userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public CacheablePage<InventoryLogResponse> getLogsByUser(Long userId, Pageable pageable) {
        log.debug("📋 Fetching logs by user: userId={}", userId);

        Page<InventoryLogResponse> page = inventoryLogRepository
                .findByPerformedByIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);

        return CacheablePage.of(page);
    }

    /**
     * Get logs by date range
     * Don't cache this - users may query different date ranges
     */
    @Override
    @Transactional(readOnly = true)
    public CacheablePage<InventoryLogResponse> getLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        log.debug("📋 Fetching logs by date range: {} to {}", startDate, endDate);

        Page<InventoryLogResponse> page = inventoryLogRepository
                .findByDateRange(startDate, endDate, pageable)
                .map(this::mapToResponse);

        return CacheablePage.of(page);
    }

    private InventoryLogResponse mapToResponse(InventoryLogEntity log) {
        return InventoryLogResponse.builder()
                .id(log.getId())
                .productId(log.getProduct().getId())
                .productName(log.getProduct().getName())
                .quantityChange(log.getQuantityChange())
                .stockBefore(log.getStockBefore())
                .stockAfter(log.getStockAfter())
                .operation(log.getOperation())
                .operationDisplay(getOperationDisplay(log.getOperation()))
                .performedById(log.getPerformedBy() != null ? log.getPerformedBy().getId() : null)
                .performedByName(log.getPerformedBy() != null ? log.getPerformedBy().getName() : null)
                .orderId(log.getOrder() != null ? log.getOrder().getId() : null)
                .notes(log.getNotes())
                .referenceCode(log.getReferenceCode())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String getOperationDisplay(OperationType operation) {
        return switch (operation) {
            case PURCHASE -> "Mua hàng";
            case RESTOCK -> "Nhập kho";
            case RETURN -> "Trả hàng";
            case ADJUSTMENT -> "Điều chỉnh";
            case DAMAGED -> "Hàng hỏng";
            case LOST -> "Mất hàng";
        };
    }
}