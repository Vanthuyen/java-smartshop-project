package com.example.smartshop.entities;

import com.example.smartshop.commons.enums.OperationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_logs")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryLogEntity extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    private Integer quantityChange;

    @Enumerated(EnumType.STRING)
    private OperationType operation;
}
