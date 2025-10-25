package com.example.smartshop.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name", "deleted_at"})
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CategoryEntity extends BaseEntity{
        @Column(nullable = false)
        private String name;

        private String description;

        @OneToMany(mappedBy = "category")
        private List<ProductEntity> products;
}
