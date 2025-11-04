package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.CategoryRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.CategoryResponse;
import org.springframework.data.domain.Page;

public interface CategoryService {
    CacheablePage<CategoryResponse> findAllCategories(int page, int size, String search);
    CategoryResponse GetCategoryById(Long id);
    CategoryResponse createCategory(CategoryRequest categoryRequest);
    CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest);
    void deleteCategory(Long id);
}
