package com.example.vstock.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 완성된 1분봉. 진행 중인 봉은 메모리에서만 관리하고, 분이 끝나면 한 번 저장한다.
 * 5분봉·1시간봉·일봉은 저장하지 않고 조회할 때 이 테이블을 집계한다.
 */
@Getter
@Entity
@Table(name = "stock_candles")
@IdClass(StockCandleId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCandle {

    @Id
    private Long stockId;

    @Id
    private LocalDateTime startTime;

    @Column(nullable = false)
    private Long openPrice;

    @Column(nullable = false)
    private Long highPrice;

    @Column(nullable = false)
    private Long lowPrice;

    @Column(nullable = false)
    private Long closePrice;

    @Column(nullable = false)
    private Long volume;

    public StockCandle(Long stockId, LocalDateTime startTime, Long openPrice, Long highPrice,
                       Long lowPrice, Long closePrice, Long volume) {
        this.stockId = stockId;
        this.startTime = startTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }
}
