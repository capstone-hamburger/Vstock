package com.example.vstock.domain.userstock.repository;

import com.example.vstock.domain.userstock.entity.UserStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStockRepository extends JpaRepository<UserStock, Long> {
}
