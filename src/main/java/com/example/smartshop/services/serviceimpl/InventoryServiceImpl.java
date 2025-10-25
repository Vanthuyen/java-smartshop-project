package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.OperationType;
import com.example.smartshop.commons.exceptions.InvalidQuantityException;
import com.example.smartshop.commons.exceptions.ProductNotFoundException;
import com.example.smartshop.entities.InventoryLogEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.repositories.InventoryLogRepository;
import com.example.smartshop.repositories.ProductRepository;
import com.example.smartshop.services.InventoryService;
import com.example.smartshop.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
    public void restock(Long productId, int quantity, Long operatorUserId) {
        log.info("Restocking product: productId={}, quantity={}, operatorId={}",
                productId, quantity, operatorUserId);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        ProductEntity p = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        int prev = p.getStock();
        p.setStock(prev + quantity);
        productRepository.save(p);

        InventoryLogEntity log = new InventoryLogEntity();
        log.setProduct(p);
        log.setQuantityChange(quantity);
        log.setOperation(OperationType.RESTOCK);
        inventoryLogRepository.save(log);
        redisService.updateStock(productId, p.getStock());
    }

    @Override
    @Transactional
    public void purchase(Long productId, int quantity, Long orderId, Long customerId) {
        log.info("Processing purchase: productId={}, quantity={}, orderId={}, customerId={}",
                productId, quantity, orderId, customerId);
        if (quantity <= 0) throw new InvalidQuantityException(quantity);

        ProductEntity p = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (p.getStock() < quantity) {
            throw new RuntimeException("Not enough stock for productId=" + productId);
        }
        p.setStock(p.getStock() - quantity);
        productRepository.save(p);

        InventoryLogEntity log = new InventoryLogEntity();
        log.setProduct(p);
        log.setQuantityChange(-quantity);
        log.setOperation(OperationType.PURCHASE);
        inventoryLogRepository.save(log);

        redisService.updateStock(productId, p.getStock());

    }

    @Override
    @Transactional
    public void purchaseMultiple(Map<Long, Integer> items, Long orderId, Long customerId) {

        // Validate items
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items cannot be empty");
        }

        // Validate all quantities
        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new InvalidQuantityException(entry.getValue());
            }
        }
        // sort ids to avoid deadlocks
        List<Long> ids = items.keySet().stream().sorted().collect(Collectors.toList());

        List<ProductEntity> products = productRepository.findAllByIdInForUpdate(ids);
        Map<Long, ProductEntity> byId = products.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));

        for (Map.Entry<Long, Integer> e : items.entrySet()) {
            Long id = e.getKey();
            int qty = e.getValue();
            ProductEntity p = byId.get(id);
            if (p == null) throw new RuntimeException("Product not found: " + id);
            if (p.getStock() < qty)
                throw new RuntimeException("Not enough stock for product " + id);
        }

        for (Map.Entry<Long, Integer> e : items.entrySet()) {
            Long id = e.getKey();
            int qty = e.getValue();
            ProductEntity p = byId.get(id);
            p.setStock(p.getStock() - qty);
        }
        productRepository.saveAll(products);

        // create logs
        List<InventoryLogEntity> logs = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : items.entrySet()) {
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
}
