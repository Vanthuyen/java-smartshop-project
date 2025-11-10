package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.models.dtos.requets.CategoryRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.CategoryResponse;
import com.example.smartshop.models.mappers.CaterogyMapper;
import com.example.smartshop.repositories.CategoryRepository;
import com.example.smartshop.services.CategoryService;
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
 * Category Service Implementation with Redis Cache
 *
 * Cache Strategy:
 * - Category detail (30 min): Categories rarely change
 * - Categories list (20 min): List changes less frequently than products
 *
 * @version 2.0
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CaterogyMapper caterogyMapper;

    /**
     * Get all categories with pagination and search
     *
     * Cache key: page-size-search
     * TTL: 20 minutes (categories don't change often)
     *
     * Example keys:
     * - categories::0-20-null
     * - categories::0-20-electronics
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "categories",
            key = "#page + '-' + #size + '-' + (#search != null ? #search : 'null')",
            unless = "#result == null || #result.isEmpty()"
    )
    public CacheablePage<CategoryResponse> findAllCategories(int page, int size, String search) {
        log.debug("📂 Fetching categories from DB: page={}, size={}, search={}", page, size, search);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CategoryEntity> categories;

        if (search != null && !search.isBlank()) {
            categories = categoryRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(search, pageable);
        } else {
            categories = categoryRepository.findByDeletedAtIsNull(pageable);
        }

        log.debug("✅ Found {} categories", categories.getTotalElements());
        return CacheablePage.of(categories.map(caterogyMapper::toResponse));
    }

    /**
     * Get category by ID
     *
     * Cache key: category ID
     * TTL: 30 minutes (categories rarely change)
     *
     * Example: category::5
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "category",
            key = "#id",
            unless = "#result == null"
    )
    public CategoryResponse GetCategoryById(Long id) {
        log.debug("📂 Fetching category from DB: id={}", id);

        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        return caterogyMapper.toResponse(category);
    }

    /**
     * Create new category
     *
     * Cache eviction:
     * - Clear categories list cache (new category affects list)
     * - No need to clear category detail (doesn't exist yet)
     */
    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        log.info("🆕 Creating new category: name={}", categoryRequest.getName());

        // Check duplicate
        if (categoryRepository.existsByNameAndDeletedAtIsNull(categoryRequest.getName())) {
            throw new RuntimeException(
                    "Category with name '" + categoryRequest.getName() + "' already exists");
        }

        CategoryEntity category = CategoryEntity.builder()
                .name(categoryRequest.getName())
                .description(categoryRequest.getDescription())
                .build();

        CategoryEntity savedCategory = categoryRepository.save(category);

        log.info("✅ Category created: id={}, name={}", savedCategory.getId(), savedCategory.getName());
        log.debug("🗑️ Evicted cache: categories (all entries)");

        return caterogyMapper.toResponse(savedCategory);
    }

    /**
     * Update existing category
     *
     * Cache strategy:
     * - Use @CachePut to update category detail cache
     * - Evict categories list cache
     * - Also evict products cache (product responses include category name)
     */
    @Override
    @Transactional
    @Caching(
            put = @CachePut(value = "category", key = "#id"),
            evict = {
                    @CacheEvict(value = "categories", allEntries = true),
                    @CacheEvict(value = "products", allEntries = true)  // Products show category name
            }
    )
    public CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest) {
        log.info("✏️ Updating category: id={}", id);

        CategoryEntity updateCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        // Check duplicate name (only if name changed)
        if (!updateCategory.getName().equals(categoryRequest.getName()) &&
                categoryRepository.existsByNameAndDeletedAtIsNull(categoryRequest.getName())) {
            throw new RuntimeException(
                    "Category with name '" + categoryRequest.getName() + "' already exists");
        }

        updateCategory.setName(categoryRequest.getName());
        updateCategory.setDescription(categoryRequest.getDescription());

        CategoryEntity updatedCategory = categoryRepository.save(updateCategory);

        log.info("✅ Category updated: id={}, name={}", updatedCategory.getId(), updatedCategory.getName());
        log.debug("🔄 Updated cache: category::{}", id);
        log.debug("🗑️ Evicted cache: categories (all), products (all)");

        return caterogyMapper.toResponse(updatedCategory);
    }

    /**
     * Delete category (soft delete)
     *
     * Cache eviction:
     * - Clear category detail cache
     * - Clear categories list cache
     * - Clear products cache (products in this category affected)
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "category", key = "#id"),
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)  // Products in this category
    })
    public void deleteCategory(Long id) {
        log.info("🗑️ Deleting category: id={}", id);

        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        // Soft delete
        categoryRepository.softDelete(category.getId());

        log.info("✅ Category deleted: id={}, name={}", id, category.getName());
        log.debug("🗑️ Evicted cache: category::{}, categories (all), products (all)", id);
    }
}