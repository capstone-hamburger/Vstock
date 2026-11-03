package com.example.vstock.domain.stock.repository;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.entity.StockCandleId;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockCandleRepository extends JpaRepository<StockCandle, StockCandleId> {

    /**
     * 같은 (종목, 간격, 시작 시각)이 이미 있으면 덮어쓴다. 마감 저장이 두 번 호출돼도 안전하다.
     * 간격은 {@code @Enumerated(STRING)}과 같은 형태(상수 이름)로 저장한다.
     */
    @Transactional
    default void upsertCandle(Long stockId, CandleInterval interval, LocalDateTime startTime,
                              Long open, Long high, Long low, Long close, Long volume) {
        upsertCandleRow(stockId, interval.name(), startTime, open, high, low, close, volume);
    }

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO stock_candles
                (stock_id, candle_interval, start_time, open_price, high_price, low_price, close_price, volume)
            VALUES
                (:stockId, :interval, :startTime, :open, :high, :low, :close, :volume) AS new_candle
            ON DUPLICATE KEY UPDATE
                open_price = new_candle.open_price,
                high_price = new_candle.high_price,
                low_price = new_candle.low_price,
                close_price = new_candle.close_price,
                volume = new_candle.volume
            """, nativeQuery = true)
    void upsertCandleRow(@Param("stockId") Long stockId, @Param("interval") String interval,
                         @Param("startTime") LocalDateTime startTime, @Param("open") Long open,
                         @Param("high") Long high, @Param("low") Long low, @Param("close") Long close,
                         @Param("volume") Long volume);

    /** 시작 시각이 [from, before)인 봉을 최신순으로 조회한다. PK 범위 스캔이라 limit만큼만 읽는다. */
    @Query("""
            select c from StockCandle c
            where c.stockId = :stockId and c.candleInterval = :interval
              and c.timestamp >= :from and c.timestamp < :before
            order by c.timestamp desc
            """)
    List<StockCandle> findCandlesBefore(@Param("stockId") Long stockId, @Param("interval") CandleInterval interval,
                                        @Param("from") LocalDateTime from, @Param("before") LocalDateTime before,
                                        Pageable pageable);

    /** 시작 시각이 [from, to)인 봉을 오래된 순으로 조회한다. 서버 시작 시 진행 중인 봉을 복구할 때 쓴다. */
    @Query("""
            select c from StockCandle c
            where c.stockId = :stockId and c.candleInterval = :interval
              and c.timestamp >= :from and c.timestamp < :to
            order by c.timestamp asc
            """)
    List<StockCandle> findCandlesAscending(@Param("stockId") Long stockId, @Param("interval") CandleInterval interval,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
