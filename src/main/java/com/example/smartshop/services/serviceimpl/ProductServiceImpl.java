package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.models.dtos.requets.ProductRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.ProductResponse;
import com.example.smartshop.models.mappers.ProductMapper;
import com.example.smartshop.repositories.CategoryRepository;
import com.example.smartshop.repositories.ProductRepository;
import com.example.smartshop.services.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product Service Implementation with Redis Cache
 *
 * Cache Strategy:
 * - Product detail (15 min): Cache individual products by ID
 * - Products list (5 min): Cache paginated lists with search/filter
 * - Cache eviction: Clear relevant caches on create/update/delete
 *
 * @version 2.0
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductMapper productMapper;

    /**
     * Get all products with pagination, search and category filter
     *
     * Cache key includes all parameters to ensure correct data is returned
     * TTL: 5 minutes (products change frequently)
     *
     * Example keys:
     * - products::0-20-null-null (page 0, size 20, no search, no filter)
     * - products::0-20-iphone-null (page 0, size 20, search "iphone")
     * - products::0-20-null-5 (page 0, size 20, category 5)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "products",
            key = "#page + '-' + #size + '-' + (#search != null ? #search : 'null') + '-' + (#categoryId != null ? #categoryId : 'null')",
            unless = "#result == null || #result.isEmpty()"
    )
    public CacheablePage<ProductResponse> getAllProducts(int page, int size, String search, Long categoryId) {
        log.debug("📊 Fetching products from DB: page={}, size={}, search={}, categoryId={}",
                page, size, search, categoryId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ProductEntity> products;

        // Query based on filters
        if (search != null && !search.isBlank() && categoryId != null) {
            products = productRepository.findByNameContainingIgnoreCaseAndCategory_IdAndDeletedAtIsNull(
                    search, categoryId, pageable);
        } else if (search != null && !search.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(search, pageable);
        } else if (categoryId != null) {
            products = productRepository.findByCategory_IdAndDeletedAtIsNull(categoryId, pageable);
        } else {
            products = productRepository.findByDeletedAtIsNull(pageable);
        }

        log.debug("✅ Found {} products", products.getTotalElements());
        return CacheablePage.of(products.map(productMapper::toResponse));
    }

    /**
     * Get product by ID
     *
     * Cache key: product ID
     * TTL: 15 minutes (product details change infrequently)
     *
     * Example: product::123
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "product",
            key = "#id",
            unless = "#result == null"
    )
    public ProductResponse getProductById(Long id) {
        log.debug("📦 Fetching product from DB: id={}", id);

        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        return productMapper.toResponse(product);
    }

    /**
     * Create new product
     *
     * Cache eviction:
     * - Clear ALL products list cache (new product affects pagination)
     * - Don't need to clear product detail cache (doesn't exist yet)
     */
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        log.info("🆕 Creating new product: name={}, categoryId={}",
                request.getName(), request.getCategoryId());

        // Validate category exists
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException(
                        "Category not found with id: " + request.getCategoryId()));

        // Check duplicate name in same category
        if (productRepository.existsByNameAndCategoryIdAndDeletedAtIsNull(
                request.getName(), category.getId())) {
            throw new RuntimeException(
                    "Product with name '" + request.getName() + "' already exists in this category");
        }

        // Create product
        ProductEntity product = ProductEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .category(category)
                .build();

        ProductEntity savedProduct = productRepository.save(product);

        log.info("✅ Product created: id={}, name={}", savedProduct.getId(), savedProduct.getName());
        log.debug("🗑️ Evicted cache: products (all entries)");

        return productMapper.toResponse(savedProduct);
    }

    /**
     * Update existing product
     *
     * Cache strategy:
     * - Use @CachePut to UPDATE product detail cache (avoid cache miss on next read)
     * - Evict products list cache (pagination may change)
     * - Evict productStock cache if stock changed
     *
     * Note: @CachePut ALWAYS executes method and updates cache with return value
     */
    @Override
    @Transactional
    @Caching(
            put = @CachePut(value = "product", key = "#id"),
            evict = {
                    @CacheEvict(value = "products", allEntries = true),
                    @CacheEvict(value = "productStock", key = "#id")
            }
    )
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.info("✏️ Updating product: id={}", id);

        // Find existing product
        ProductEntity updateProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Validate category
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException(
                        "Category not found with id: " + request.getCategoryId()));

        // Check duplicate name (only if name changed)
        if (!updateProduct.getName().equals(request.getName()) &&
                productRepository.existsByNameAndCategoryIdAndDeletedAtIsNull(
                        request.getName(), request.getCategoryId())) {
            throw new RuntimeException(
                    "Product with name '" + request.getName() + "' already exists in this category");
        }

        // Update fields
        updateProduct.setName(request.getName());
        updateProduct.setDescription(request.getDescription());
        updateProduct.setPrice(request.getPrice());
        updateProduct.setStock(request.getStock());
        updateProduct.setCategory(category);

        ProductEntity updatedProduct = productRepository.save(updateProduct);

        log.info("✅ Product updated: id={}, name={}", updatedProduct.getId(), updatedProduct.getName());
        log.debug("🔄 Updated cache: product::{}", id);
        log.debug("🗑️ Evicted cache: products (all), productStock::{}", id);

        return productMapper.toResponse(updatedProduct);
    }

    /**
     * Delete product (soft delete)
     *
     * Cache eviction:
     * - Clear product detail cache
     * - Clear products list cache
     * - Clear product stock cache
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productStock", key = "#id")
    })
    public void deleteProduct(Long id) {
        log.info("🗑️ Deleting product: id={}", id);

        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Soft delete
        productRepository.softDelete(product.getId());

        log.info("✅ Product deleted: id={}, name={}", id, product.getName());
        log.debug("🗑️ Evicted cache: product::{}, products (all), productStock::{}", id, id);
    }
}