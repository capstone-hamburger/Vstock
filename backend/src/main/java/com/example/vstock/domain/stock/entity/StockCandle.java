package com.example.vstock.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마감된 봉. 간격(1m, 10m, 1h, 1d, 1M)마다 행이 따로 있고, 봉이 닫힐 때 한 번 저장한다.
 * 아직 닫히지 않은 봉은 메모리에서만 관리한다.
 *
 * 조회는 요청한 간격의 행만 PK 범위로 읽으므로, 간격이 커져도 읽는 행 수는 limit으로 고정된다.
 * (interval은 MySQL 예약어라 컬럼명을 candle_interval로 쓴다.)
 *
 * <p>Hibernate는 복합키 컬럼을 속성 이름순으로 만든다. 속성 이름을 candleInterval, stockId, timestamp로
 * 둔 것은 PK가 (candle_interval, stock_id, start_time) 순서가 되게 하려는 것이다. 조회 조건이
 * "간격 일치, 종목 일치, 시간 범위"라서 이 순서여야 PK 범위 스캔으로 limit개만 읽는다.
 * 속성 이름을 바꾸면 순서가 달라지므로 StockCandleRepositoryTest의 PK 순서 테스트가 깨진다.
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
    @Enumerated(EnumType.STRING)
    @Column(name = "candle_interval", columnDefinition = "VARCHAR(30)")
    private CandleInterval candleInterval;

    @Id
    @Column(name = "start_time")
    private LocalDateTime timestamp;

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

    public StockCandle(Long stockId, CandleInterval candleInterval, LocalDateTime startTime, Long openPrice,
                       Long highPrice, Long lowPrice, Long closePrice, Long volume) {
        this.stockId = stockId;
        this.candleInterval = candleInterval;
        this.timestamp = startTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }
}
