package com.example.smartshop.repositories;

import com.example.smartshop.entities.InventoryLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLogEntity, Long> {
    @Query("SELECT il FROM InventoryLogEntity il " +
            "LEFT JOIN FETCH il.product " +
            "LEFT JOIN FETCH il.performedBy " +
            "LEFT JOIN FETCH il.order " +
            "ORDER BY il.createdAt DESC")
    Page<InventoryLogEntity> findAllWithDetails(Pageable pageable);

    @Query("SELECT il FROM InventoryLogEntity il " +
            "LEFT JOIN FETCH il.product p " +
            "LEFT JOIN FETCH il.performedBy " +
            "LEFT JOIN FETCH il.order " +
            "WHERE p.id = :productId " +
            "ORDER BY il.createdAt DESC")
    Page<InventoryLogEntity> findByProductIdOrderByCreatedAtDesc(
            @Param("productId") Long productId,
            Pageable pageable
    );

    List<InventoryLogEntity> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Page<InventoryLogEntity> findByPerformedByIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    @Query("SELECT il FROM InventoryLogEntity il " +
            "WHERE il.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY il.createdAt DESC")
    Page<InventoryLogEntity> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
