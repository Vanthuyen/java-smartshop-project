package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.CategoryRequest;
import com.example.smartshop.models.dtos.requets.ProductRequest;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.models.dtos.responses.CategoryResponse;
import com.example.smartshop.models.dtos.responses.ProductResponse;
import com.example.smartshop.services.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Category Management", description = "APIs for managing categories")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories (Admin only)")
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {

        Page<CategoryResponse> categories = categoryService.findAllCategories(page, size, search);
        return ResponseUtil.success("Get all categories successfully", categories);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get detail category (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> GetCategoryById(@PathVariable Long id) {
        CategoryResponse categoryResponse = categoryService.GetCategoryById(id);
        return ResponseUtil.success("Get category successfully", categoryResponse);
    }

    @PostMapping
    @Operation(summary = "Create new category (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> CreateCategory(@Valid @RequestBody CategoryRequest categoryRequest) {
        CategoryResponse categoryResponse = categoryService.createCategory(categoryRequest);
        return ResponseUtil.success("Create category successfully", categoryResponse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product (Admin only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@Valid @RequestBody CategoryRequest categoryRequest, @PathVariable Long id) {
        CategoryResponse category = categoryService.updateCategory(id, categoryRequest);
        return ResponseUtil.success("Update Category Successfully", category);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete category (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseUtil.success("Category deleted successfully", null);
    }
}
