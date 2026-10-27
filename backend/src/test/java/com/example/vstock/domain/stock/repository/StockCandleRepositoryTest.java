package com.example.vstock.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.support.RepositoryTestSupport;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class StockCandleRepositoryTest extends RepositoryTestSupport {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    @Autowired
    private StockCandleRepository candleRepository;

    @Test
    void 같은_분을_다시_저장하면_덮어쓴다() {
        candleRepository.upsertCandle(STOCK_ID, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L);
        candleRepository.upsertCandle(STOCK_ID, NINE, 10_000L, 10_500L, 9_900L, 10_400L, 25L);
        flushAndClear();

        List<StockCandle> found = candleRepository.findCandlesAscending(STOCK_ID, NINE, NINE);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getHighPrice()).isEqualTo(10_500L);
        assertThat(found.get(0).getVolume()).isEqualTo(25L);
    }

    @Test
    void 최신순으로_limit개만_조회한다() {
        for (int i = 0; i < 5; i++) {
            candleRepository.upsertCandle(STOCK_ID, NINE.plusMinutes(i), 1L, 1L, 1L, 1L, 1L);
        }

        List<StockCandle> found = candleRepository.findLatestCandles(
                STOCK_ID, NINE, NINE.plusMinutes(5), PageRequest.of(0, 2));

        assertThat(found).extracting(StockCandle::getStartTime)
                .containsExactly(NINE.plusMinutes(4), NINE.plusMinutes(3));
    }

    @Test
    void 조회_구간의_끝_시각은_포함하지_않는다() {
        candleRepository.upsertCandle(STOCK_ID, NINE, 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, NINE.plusMinutes(1), 1L, 1L, 1L, 1L, 1L);

        List<StockCandle> found = candleRepository.findLatestCandles(
                STOCK_ID, NINE, NINE.plusMinutes(1), PageRequest.of(0, 10));

        assertThat(found).hasSize(1);
    }

    @Test
    void 분봉을_5분봉으로_집계한다() {
        // 09:00~09:04 구간
        candleRepository.upsertCandle(STOCK_ID, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L);
        candleRepository.upsertCandle(STOCK_ID, NINE.plusMinutes(1), 10_100L, 10_500L, 10_000L, 10_400L, 20L);
        candleRepository.upsertCandle(STOCK_ID, NINE.plusMinutes(4), 10_400L, 10_450L, 9_800L, 9_900L, 5L);
        // 09:05~09:09 구간
        candleRepository.upsertCandle(STOCK_ID, NINE.plusMinutes(5), 9_900L, 9_950L, 9_700L, 9_800L, 7L);

        List<Object[]> rows = candleRepository.findAggregatedCandles(
                STOCK_ID, NINE, NINE.plusMinutes(10), 300, 10);

        // 최신 구간이 먼저 나온다
        assertThat(rows).hasSize(2);
        Object[] first = rows.get(1);
        assertThat(toStart(first)).isEqualTo(NINE);
        assertThat(((Number) first[1]).longValue()).isEqualTo(10_000L);
        assertThat(((Number) first[2]).longValue()).isEqualTo(10_500L);
        assertThat(((Number) first[3]).longValue()).isEqualTo(9_800L);
        assertThat(((Number) first[4]).longValue()).isEqualTo(9_900L);
        assertThat(((Number) first[5]).longValue()).isEqualTo(35L);
    }

    @Test
    void 일봉은_현지_자정을_기준으로_묶는다() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 10, 27, 0, 0, 0);
        candleRepository.upsertCandle(STOCK_ID, dayStart.plusHours(9), 100L, 120L, 90L, 110L, 1L);
        candleRepository.upsertCandle(STOCK_ID, dayStart.plusHours(15), 110L, 130L, 105L, 125L, 2L);
        candleRepository.upsertCandle(STOCK_ID, dayStart.plusDays(1).plusHours(9), 125L, 126L, 120L, 121L, 3L);

        List<Object[]> rows = candleRepository.findAggregatedCandles(
                STOCK_ID, dayStart, dayStart.plusDays(2), 86_400, 10);

        assertThat(rows).hasSize(2);
        Object[] first = rows.get(1);
        assertThat(toStart(first)).isEqualTo(dayStart);
        assertThat(((Number) first[1]).longValue()).isEqualTo(100L);
        assertThat(((Number) first[4]).longValue()).isEqualTo(125L);
        assertThat(((Number) first[5]).longValue()).isEqualTo(3L);
    }

    private LocalDateTime toStart(Object[] row) {
        return LocalDateTime.ofEpochSecond(((Number) row[0]).longValue(), 0, ZoneOffset.UTC);
    }
}
