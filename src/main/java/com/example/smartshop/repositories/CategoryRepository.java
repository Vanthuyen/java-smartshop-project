package com.example.smartshop.repositories;

import com.example.smartshop.entities.CategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    boolean existsByNameAndDeletedAtIsNull(String name);

    @Modifying
    @Query("UPDATE CategoryEntity p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.deletedAt IS NULL")
    void softDelete(@Param("id") Long id);


    Page<CategoryEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<CategoryEntity> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name, Pageable pageable);
}
