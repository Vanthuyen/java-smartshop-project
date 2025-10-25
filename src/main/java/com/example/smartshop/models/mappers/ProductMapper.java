package com.example.smartshop.models.mappers;

import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.models.dtos.requets.ProductRequest;
import com.example.smartshop.models.dtos.responses.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    ProductResponse toResponse(ProductEntity product);

    @Mapping(source = "categoryId", target = "category.id")
    ProductEntity toEntity(ProductRequest request);
}
