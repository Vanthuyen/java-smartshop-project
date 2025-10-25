package com.example.smartshop.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


import java.math.BigDecimal;

@Entity
@Table(name = "products",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name", "category_id", "deleted_at"})
        },
        indexes = {
                @Index(columnList = "name"),
                @Index(columnList = "price")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ProductEntity extends BaseEntity{

        @Column(nullable = false)
        private String name;

        private String description;

        @Column(nullable = false)
        private BigDecimal price;

        @Column(nullable = false)
        private Integer stock;

        @ManyToOne
        @JoinColumn(name = "category_id", nullable = false)
        private CategoryEntity category;
}
