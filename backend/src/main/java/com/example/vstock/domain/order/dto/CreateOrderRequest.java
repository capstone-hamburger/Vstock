package com.example.vstock.domain.order.dto;

import com.example.vstock.domain.order.entity.OrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotNull
    @Positive
    private Long stockId;

    @NotNull
    private OrderType type;

    @NotNull
    @Positive
    private Integer quantity;

    @NotNull
    @Positive
    private Long price;

    public CreateOrderRequest(Long stockId, OrderType type, Integer quantity, Long price) {
        this.stockId = stockId;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
    }
}
