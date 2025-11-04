package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.OperationType;
import com.example.smartshop.commons.exceptions.InvalidQuantityException;
import com.example.smartshop.commons.exceptions.ProductNotFoundException;
import com.example.smartshop.entities.InventoryLogEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.models.dtos.requets.PurchaseMultiRequest;
import com.example.smartshop.models.dtos.requets.PurchaseRequest;
import com.example.smartshop.models.dtos.requets.RestockRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.InventoryLogResponse;
import com.example.smartshop.repositories.InventoryLogRepository;
import com.example.smartshop.repositories.ProductRepository;
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

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void restock(RestockRequest request) {
        log.info("Restocking product: productId={}, quantity={}, operatorId={}",
                request.getProductId(), request.getQuantity(), request.getOperatorId());
        if (request.getQuantity() <= 0) throw new IllegalArgumentException("Quantity must be positive");

        ProductEntity p = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));

        int prev = p.getStock();
        p.setStock(prev + request.getQuantity());
        productRepository.save(p);

        InventoryLogEntity log = new InventoryLogEntity();
        log.setProduct(p);
        log.setQuantityChange(request.getQuantity());
        log.setOperation(OperationType.RESTOCK);
        inventoryLogRepository.save(log);
        redisService.updateStock(request.getProductId(), p.getStock());
    }

    @Override
    @Transactional
    public void purchase(PurchaseRequest request) {
        log.info("Processing purchase: productId={}, quantity={}, orderId={}, customerId={}",
                request.getProductId(), request.getQuantity(), request.getOrderId(), request.getCustomerId());
        if (request.getQuantity() <= 0) throw new InvalidQuantityException(request.getQuantity());

        ProductEntity p = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        if (p.getStock() < request.getQuantity()) {
            throw new RuntimeException("Not enough stock for productId=" + request.getProductId());
        }
        p.setStock(p.getStock() - request.getQuantity());
        productRepository.save(p);

        InventoryLogEntity log = new InventoryLogEntity();
        log.setProduct(p);
        log.setQuantityChange(-request.getQuantity());
        log.setOperation(OperationType.PURCHASE);
        inventoryLogRepository.save(log);

        redisService.updateStock(request.getProductId(), p.getStock());

    }

    @Override
    @Transactional
    public void purchaseMultiple(PurchaseMultiRequest request) {

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
        // sort ids to avoid deadlocks
        List<Long> ids = request.getItems().keySet().stream().sorted().collect(Collectors.toList());

        List<ProductEntity> products = productRepository.findAllByIdInForUpdate(ids);
        Map<Long, ProductEntity> byId = products.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));

        for (Map.Entry<Long, Integer> e : request.getItems().entrySet()) {
            Long id = e.getKey();
            int qty = e.getValue();
            ProductEntity p = byId.get(id);
            if (p == null) throw new RuntimeException("Product not found: " + id);
            if (p.getStock() < qty)
                throw new RuntimeException("Not enough stock for product " + id);
        }

        for (Map.Entry<Long, Integer> e : request.getItems().entrySet()) {
            Long id = e.getKey();
            int qty = e.getValue();
            ProductEntity p = byId.get(id);
            p.setStock(p.getStock() - qty);
        }
        productRepository.saveAll(products);

        // create logs
        List<InventoryLogEntity> logs = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : request.getItems().entrySet()) {
            InventoryLogEntity log = new InventoryLogEntity();
            log.setProduct(byId.get(e.getKey()));
            log.setQuantityChange(-e.getValue());
            log.setOperation(OperationType.PURCHASE);
            logs.add(log);
        }
        inventoryLogRepository.saveAll(logs);
        for (ProductEntity p : products) {
            redisService.updateStock(p.getId(), p.getStock());
        }
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
    public Page<InventoryLogResponse> getLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return inventoryLogRepository.findByDateRange(startDate, endDate, pageable)
                .map(this::mapToResponse);
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
