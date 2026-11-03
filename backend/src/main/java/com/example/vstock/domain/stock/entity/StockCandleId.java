package com.example.vstock.domain.stock.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StockCandleId implements Serializable {

    private Long stockId;
    private CandleInterval candleInterval;
    private LocalDateTime timestamp;
}
