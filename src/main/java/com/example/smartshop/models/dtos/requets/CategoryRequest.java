package com.example.smartshop.models.dtos.requets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(min = 3, max = 255, message = "Category name must be between 3 and 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}
