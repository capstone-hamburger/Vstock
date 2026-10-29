package com.example.vstock.domain.order.dto;

import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderResponse {

    private final Long orderId;
    private final Long userId;
    private final Long stockId;
    private final OrderType type;
    private final OrderStatus status;
    private final int quantity;
    private final int filledQuantity;
    private final Long price;
    private final LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(), order.getUser().getUserId(), order.getStock().getStockId(),
                order.getType(), order.getStatus(), order.getQuantity(), order.getFilledQuantity(),
                order.getPrice(), order.getCreatedAt());
    }
}
