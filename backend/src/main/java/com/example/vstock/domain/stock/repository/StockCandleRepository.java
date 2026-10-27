package com.example.vstock.domain.stock.repository;

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

    /** 같은 (종목, 시작 시각)이 이미 있으면 덮어쓴다. 마감 저장이 두 번 호출돼도 안전하다. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO stock_candles
                (stock_id, start_time, open_price, high_price, low_price, close_price, volume)
            VALUES
                (:stockId, :startTime, :open, :high, :low, :close, :volume) AS new_candle
            ON DUPLICATE KEY UPDATE
                open_price = new_candle.open_price,
                high_price = new_candle.high_price,
                low_price = new_candle.low_price,
                close_price = new_candle.close_price,
                volume = new_candle.volume
            """, nativeQuery = true)
    void upsertCandle(@Param("stockId") Long stockId, @Param("startTime") LocalDateTime startTime,
                      @Param("open") Long open, @Param("high") Long high, @Param("low") Long low,
                      @Param("close") Long close, @Param("volume") Long volume);

    /** [from, to) 구간의 1분봉을 최신순으로 limit개 조회한다. */
    @Query("""
            select c from StockCandle c
            where c.stockId = :stockId and c.startTime >= :from and c.startTime < :to
            order by c.startTime desc
            """)
    List<StockCandle> findLatestCandles(@Param("stockId") Long stockId, @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to, Pageable pageable);

    /** [from, to] 구간의 1분봉을 오래된 순으로 조회한다. 서버 시작 시 진행 중인 봉을 복구할 때 쓴다. */
    @Query("""
            select c from StockCandle c
            where c.stockId = :stockId and c.startTime >= :from and c.startTime <= :to
            order by c.startTime asc
            """)
    List<StockCandle> findCandlesAscending(@Param("stockId") Long stockId, @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 1분봉을 bucketSeconds 단위로 묶어 [from, to) 구간의 최신 limit개 봉을 만든다.
     * 컬럼 순서: bucket_epoch, open, high, low, close, volume.
     * bucket_epoch는 시간대 보정 없이 현지 시각을 UTC로 간주한 epoch 초다.
     * open은 구간의 첫 1분봉, close는 마지막 1분봉 값이다.
     */
    @Query(value = """
            SELECT t.bucket AS bucket_epoch,
                   MAX(CASE WHEN t.rn_asc = 1 THEN t.open_price END) AS open_price,
                   MAX(t.high_price) AS high_price,
                   MIN(t.low_price) AS low_price,
                   MAX(CASE WHEN t.rn_desc = 1 THEN t.close_price END) AS close_price,
                   SUM(t.volume) AS volume
            FROM (
                SELECT b.bucket, b.open_price, b.high_price, b.low_price, b.close_price, b.volume,
                       ROW_NUMBER() OVER (PARTITION BY b.bucket ORDER BY b.start_time ASC) AS rn_asc,
                       ROW_NUMBER() OVER (PARTITION BY b.bucket ORDER BY b.start_time DESC) AS rn_desc
                FROM (
                    SELECT c.start_time, c.open_price, c.high_price, c.low_price, c.close_price, c.volume,
                           (TIMESTAMPDIFF(SECOND, '1970-01-01 00:00:00', c.start_time) DIV :bucketSeconds)
                               * :bucketSeconds AS bucket
                    FROM stock_candles c
                    WHERE c.stock_id = :stockId AND c.start_time >= :from AND c.start_time < :to
                ) b
            ) t
            GROUP BY t.bucket
            ORDER BY t.bucket DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findAggregatedCandles(@Param("stockId") Long stockId, @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("bucketSeconds") int bucketSeconds, @Param("limit") int limit);
}
