package com.example.vstock.domain.order.service;

import com.example.vstock.domain.order.dto.CreateOrderRequest;
import com.example.vstock.domain.order.dto.OrderResponse;
import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.order.repository.OrderRepository;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.domain.user.repository.UserRepository;
import com.example.vstock.domain.userstock.repository.UserStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final UserStockRepository userStockRepository;

    // 회원 잠금 대기 중 확정된 주문도 예약 계산에 포함한다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        User user = findUserForUpdate(userId);
        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
        Order order;
        try {
            order = Order.ofUser(user, stock, request.getType(), request.getQuantity(), request.getPrice());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        long reserved = findReservedAmount(user, stock, order.getType());
        if (order.getType() == OrderType.BUY) {
            long amount = Math.multiplyExact(order.getPrice(), (long) order.getQuantity());
            if (reserved > user.getBalance() || amount > user.getBalance() - reserved) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "주문 가능한 잔고가 부족합니다.");
            }
        } else {
            int quantity = userStockRepository.findByUserAndStock(user, stock)
                    .map(holding -> holding.getQuantity()).orElse(0);
            if (reserved > quantity || order.getQuantity() > quantity - reserved) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "매도 가능한 주식 수량이 부족합니다.");
            }
        }

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse deleteOrder(Long userId, Long orderId) {
        User user = findUserForUpdate(userId);
        Order order = orderRepository.findByOrderIdAndUser(orderId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));
        try {
            order.updateStatusToCanceled();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return OrderResponse.from(order);
    }

    private User findUserForUpdate(Long userId) {
        return userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    private long findReservedAmount(User user, Stock stock, OrderType type) {
        long reserved = 0;
        for (Order order : orderRepository.findAllByUserAndTypeAndStatus(user, type, OrderStatus.PENDING)) {
            long remaining = (long) order.getQuantity() - order.getFilledQuantity();
            if (type == OrderType.BUY) {
                reserved = Math.addExact(reserved, Math.multiplyExact(order.getPrice(), remaining));
            } else if (order.getStock().getStockId().equals(stock.getStockId())) {
                reserved = Math.addExact(reserved, remaining);
            }
        }
        return reserved;
    }
}
