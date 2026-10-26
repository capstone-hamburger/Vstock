package com.example.vstock.domain.order.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TraderType {
    USER("회원"),
    BOT("봇");

    private final String description;
}
