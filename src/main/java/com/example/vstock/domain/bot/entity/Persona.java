package com.example.vstock.domain.bot.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Persona {
    // 임시 이름
    AGGRESSIVE("공격형"),
    CONSERVATIVE("안정형"),
    MOMENTUM("추세추종형");

    private final String description;
}
