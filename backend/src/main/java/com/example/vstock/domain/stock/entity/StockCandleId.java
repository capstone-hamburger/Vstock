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

    // @ManyToOne 식별자(stock)의 PK 타입. 속성 이름은 엔티티의 stock과 같아야 한다.
    private Long stock;
    private CandleInterval candleInterval;
    private LocalDateTime timestamp;
}
