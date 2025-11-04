package com.example.smartshop.entities;

import com.example.smartshop.commons.enums.OperationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_logs")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryLogEntity extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    private Integer quantityChange;

    @Column(nullable = false)
    private Integer stockBefore;

    @Column(nullable = false)
    private Integer stockAfter;

    @Enumerated(EnumType.STRING)
    private OperationType operation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private UserEntity performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @Column(length = 500)
    private String notes;

    // Reference code (có thể là order code, import code, etc.)
    @Column(length = 100)
    private String referenceCode;
}
