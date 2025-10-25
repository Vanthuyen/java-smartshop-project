package com.example.smartshop.entities;

import com.example.smartshop.commons.enums.StatusOrder;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class OrderEntity extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    private BigDecimal totalPrice;

    private StatusOrder status;
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItemEntity> items;
}
