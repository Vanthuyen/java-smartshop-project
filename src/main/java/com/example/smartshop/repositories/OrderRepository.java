package com.example.smartshop.repositories;

import com.example.smartshop.entities.OrderEntity;
import com.example.smartshop.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends CrudRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<OrderEntity> findByUserAndDeletedAtIsNull(UserEntity user, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.product " +
            "WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderEntity> findByIdWithItemsAndDeletedAtIsNull(Long id);
}
