package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.ProductRequest;
import com.example.smartshop.models.dtos.responses.CacheablePage;
import com.example.smartshop.models.dtos.responses.ProductResponse;

public interface ProductService {
    CacheablePage<ProductResponse> getAllProducts(int page, int size, String search, Long categoryId);
    ProductResponse getProductById(Long id);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(Long id, ProductRequest request);
    void deleteProduct(Long id);
}
