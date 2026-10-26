package com.example.vstock.domain.bot.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BotSide {
    BUYER("매수자"),
    SELLER("매도자");

    private final String description;
}
