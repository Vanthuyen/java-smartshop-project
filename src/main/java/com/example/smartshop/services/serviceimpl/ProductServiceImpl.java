package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.models.dtos.requets.ProductRequest;
import com.example.smartshop.models.dtos.responses.ProductResponse;
import com.example.smartshop.models.mappers.ProductMapper;
import com.example.smartshop.repositories.CategoryRepository;
import com.example.smartshop.repositories.ProductRepository;
import com.example.smartshop.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductMapper productMapper;


    @Override
    public Page<ProductResponse> getAllProducts(int page, int size, String search, Long categoryId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ProductEntity> products;

        if (search != null && categoryId != null) {
            products = productRepository.findByNameContainingIgnoreCaseAndCategory_IdAndDeletedAtIsNull(search, categoryId, pageable);
        }

        else if (search != null) {
            products = productRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(search, pageable);
        }
        else if (categoryId != null) {
            products = productRepository.findByCategory_IdAndDeletedAtIsNull(categoryId, pageable);
        }
        else {
            products = productRepository.findByDeletedAtIsNull(pageable);
        }

        return products.map(productMapper::toResponse);
    }


    @Override
    public ProductResponse getProductById(Long id) {
        ProductEntity product = productRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Product not found with id:" + id));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.getCategoryId()).orElseThrow(
                () -> new RuntimeException("Category not found with id:" + request.getCategoryId())
        );
        if (productRepository.existsByNameAndCategoryIdAndDeletedAtIsNull(request.getName(), category.getId())) {
            throw new RuntimeException("Product with name '" + request.getName() +
                    "' already exists in this category");
        }
         ProductEntity product = new ProductEntity();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(category);

        ProductEntity savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        ProductEntity updateProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));

        if (!updateProduct.getName().equals(request.getName()) &&
                productRepository.existsByNameAndCategoryIdAndDeletedAtIsNull(
                        request.getName(), request.getCategoryId())) {
            throw new RuntimeException("Product with name '" + request.getName() +
                    "' already exists in this category");
        }

        updateProduct.setName(request.getName());
        updateProduct.setDescription(request.getDescription());
        updateProduct.setPrice(request.getPrice());
        updateProduct.setStock(request.getStock());
        updateProduct.setCategory(category);

        ProductEntity updatedProduct = productRepository.save(updateProduct);
        return productMapper.toResponse(updatedProduct);
    }


    @Override
    @Transactional
    public void deleteProduct(Long id) {
        ProductEntity product = productRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Product not found with id: " + id)
        );
        productRepository.softDelete(product.getId());
    }
}
