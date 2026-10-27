package com.example.vstock.domain.stock.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Sector {
    FINANCE("금융"),
    IT("IT"),
    MEDICAL("의료");

    private final String description;
}
