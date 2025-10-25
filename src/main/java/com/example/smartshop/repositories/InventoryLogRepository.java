package com.example.smartshop.repositories;

import com.example.smartshop.entities.InventoryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLogEntity, Long> {
}
