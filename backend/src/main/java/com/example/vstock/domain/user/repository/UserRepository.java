package com.example.vstock.domain.user.repository;

import com.example.vstock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
