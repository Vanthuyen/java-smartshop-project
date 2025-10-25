package com.example.smartshop.models.mappers;

import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.models.dtos.requets.CategoryRequest;
import com.example.smartshop.models.dtos.responses.CategoryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CaterogyMapper {
    CategoryResponse toResponse(CategoryEntity category);
    CategoryEntity toEntity(CategoryRequest categoryRequest);
}
