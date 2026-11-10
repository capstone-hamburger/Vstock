package com.example.vstock.domain.order.controller;

import com.example.vstock.domain.order.dto.CreateOrderRequest;
import com.example.vstock.domain.order.dto.OrderResponse;
import com.example.vstock.domain.order.service.OrderService;
import com.example.vstock.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(userDetails.getUserId(), request);
    }

    @DeleteMapping("/{orderId}")
    public OrderResponse deleteOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable @Positive Long orderId) {
        return orderService.deleteOrder(userDetails.getUserId(), orderId);
    }
}
