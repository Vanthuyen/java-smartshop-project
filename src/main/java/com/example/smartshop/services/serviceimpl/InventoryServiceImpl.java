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
import org.springframework.cache.annotation.Cacheable;
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

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void restock(RestockRequest request) {
        log.info("Restocking product: productId={}, quantity={}, operatorId={}",
                request.getProductId(), request.getQuantity(), request.getOperatorId());
        if (request.getQuantity() <= 0) throw new IllegalArgumentException("Quantity must be positive");

        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));
        UserEntity operator = userRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new RuntimeException("Operator not found"));

        int stockBefore = product.getStock();

        product.setStock(stockBefore + request.getQuantity());
        productRepository.save(product);

        // Create detailed log
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

        redisService.updateStock(request.getProductId(), product.getStock());
    }

    @Override
    @Transactional
    public void purchase(PurchaseRequest request) {
        log.info("Processing purchase: productId={}, quantity={}, orderId={}, customerId={}",
                request.getProductId(), request.getQuantity(), request.getOrderId(), request.getCustomerId());
        if (request.getQuantity() <= 0) throw new InvalidQuantityException(request.getQuantity());

        ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        UserEntity customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        int stockBefore = product.getStock();
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

    @Override
    @Transactional
    public void purchaseMultiple(PurchaseMultiRequest request) {
        log.info("Processing multiple purchases: {} items, orderId={}, customerId={}",
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

        log.info("Multiple purchases completed - Total items: {}, OrderId: {}",
                request.getItems().size(), request.getOrderId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "#pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public CacheablePage<InventoryLogResponse> getAllLogs(Pageable pageable) {
        Page<InventoryLogResponse> page = inventoryLogRepository
                .findAllWithDetails(pageable)
                .map(this::mapToResponse);
        return CacheablePage.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "#productId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public CacheablePage<InventoryLogResponse> getLogsByProduct(Long productId, Pageable pageable) {
        Page<InventoryLogResponse> page = inventoryLogRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::mapToResponse);
        return CacheablePage.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory-log", key = "#orderId")
    public List<InventoryLogResponse> getLogsByOrder(Long orderId) {
        return inventoryLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "inventory-log",
            key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public CacheablePage<InventoryLogResponse> getLogsByUser(Long userId, Pageable pageable) {
        Page<InventoryLogResponse> page = inventoryLogRepository
                .findByPerformedByIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);

        return CacheablePage.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public CacheablePage<InventoryLogResponse> getLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        Page<InventoryLogResponse> page = inventoryLogRepository.findByDateRange(startDate, endDate, pageable)
                .map(this::mapToResponse);
        return CacheablePage.of(page);
    }

    @Override
    @Transactional
    public void returnProduct(ReturnRequest request) {
        log.info("Processing return: productId={}, quantity={}, orderId={}, customerId={}",
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

    @Override
    @Transactional
    public void adjustStock(AdjustStockRequest request) {
        log.info("Adjusting stock: productId={}, change={}, operatorId={}, reason={}",
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
