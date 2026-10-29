package com.example.vstock.domain.order.repository;

import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUserAndTypeAndStatus(User user, OrderType type, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByOrderIdAndUser(Long orderId, User user);
}
