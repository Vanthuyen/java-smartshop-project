package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.models.dtos.requets.CategoryRequest;
import com.example.smartshop.models.dtos.responses.CategoryResponse;
import com.example.smartshop.models.mappers.CaterogyMapper;
import com.example.smartshop.repositories.CategoryRepository;
import com.example.smartshop.services.CategoryService;
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

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private CaterogyMapper caterogyMapper;

    @Override
    public Page<CategoryResponse> findAllCategories(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CategoryEntity> categories;

        if (search != null && !search.isBlank()) {
            categories = categoryRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(search, pageable);
        } else {
            categories = categoryRepository.findByDeletedAtIsNull(pageable);
        }

        return categories.map(caterogyMapper::toResponse);
    }


    @Override
    @Cacheable(value = "category", key = "#id")
    public CategoryResponse GetCategoryById(Long id) {
        CategoryEntity category = categoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Category not found with id: " + id)
        );
        return caterogyMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        if (categoryRepository.existsByNameAndDeletedAtIsNull(categoryRequest.getName())) {
            throw new RuntimeException("Category with name '" + categoryRequest.getName() +
                    "' already exists");
        }
        CategoryEntity category = new CategoryEntity();
        category.setName(categoryRequest.getName());
        category.setDescription(categoryRequest.getDescription());

        CategoryEntity savedCategory = categoryRepository.save(category);
        return caterogyMapper.toResponse(savedCategory);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "category", key = "#id"),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest) {
        CategoryEntity updateCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));


        if (!updateCategory.getName().equals(categoryRequest.getName()) &&
                categoryRepository.existsByNameAndDeletedAtIsNull(
                        categoryRequest.getName())) {
            throw new RuntimeException("Category with name '" + categoryRequest.getName() +
                    "' already exists");
        }

        updateCategory.setName(categoryRequest.getName());
        updateCategory.setDescription(categoryRequest.getDescription());

        CategoryEntity updatedCategory = categoryRepository.save(updateCategory);
        return caterogyMapper.toResponse(updatedCategory);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "category", key = "#id"),
            @CacheEvict(value = "categories", allEntries = true)
    })
    public void deleteCategory(Long id) {
        CategoryEntity category = categoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Category not found with id: " + id)
        );
        categoryRepository.softDelete(category.getId());
    }
}
