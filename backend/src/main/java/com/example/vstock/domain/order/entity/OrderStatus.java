package com.example.vstock.domain.order.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("대기"),
    COMPLETE("체결"),
    CANCELED("취소");

    private final String description;
}
