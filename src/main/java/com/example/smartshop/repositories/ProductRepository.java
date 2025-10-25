package com.example.smartshop.repositories;

import com.example.smartshop.entities.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    boolean existsByNameAndCategoryIdAndDeletedAtIsNull(String name, Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL ORDER BY p.id")
    List<ProductEntity> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE ProductEntity p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.deletedAt IS NULL")
    void softDelete(@Param("id") Long id);

    Page<ProductEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name, Pageable pageable);

    Page<ProductEntity> findByCategory_IdAndDeletedAtIsNull(Long categoryId, Pageable pageable);

    Page<ProductEntity> findByNameContainingIgnoreCaseAndCategory_IdAndDeletedAtIsNull(
            String name, Long categoryId, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductEntity> findAllByIdInAndDeletedAtIsNullForUpdate(@Param("ids") List<Long> ids);
}
