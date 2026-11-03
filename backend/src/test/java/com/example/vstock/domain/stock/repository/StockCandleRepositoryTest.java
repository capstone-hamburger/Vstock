package com.example.vstock.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.support.RepositoryTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class StockCandleRepositoryTest extends RepositoryTestSupport {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0);

    @Autowired
    private StockCandleRepository candleRepository;

    @Test
    void PK는_간격_종목_시작시각_순서라서_조회가_PK_범위_스캔이_된다() {
        @SuppressWarnings("unchecked")
        List<String> columns = em.getEntityManager().createNativeQuery("""
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'stock_candles' AND index_name = 'PRIMARY'
                ORDER BY seq_in_index
                """).getResultList();

        assertThat(columns).containsExactly("candle_interval", "stock_id", "start_time");
    }

    @Test
    void 같은_봉을_다시_저장하면_덮어쓴다() {
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_500L, 9_900L, 10_400L, 25L);
        flushAndClear();

        List<StockCandle> found = candleRepository.findCandlesAscending(
                STOCK_ID, CandleInterval.ONE_MINUTE, NINE, NINE.plusMinutes(1));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getCandleInterval()).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(found.get(0).getHighPrice()).isEqualTo(10_500L);
        assertThat(found.get(0).getVolume()).isEqualTo(25L);
    }

    @Test
    void 시작_시각이_같아도_interval이_다르면_따로_저장된다() {
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.TEN_MINUTES, NINE, 2L, 2L, 2L, 2L, 2L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MONTH, NINE, 3L, 3L, 3L, 3L, 3L);
        flushAndClear();

        assertThat(candleRepository.findCandlesBefore(
                STOCK_ID, CandleInterval.ONE_MINUTE, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(1L);
        assertThat(candleRepository.findCandlesBefore(
                STOCK_ID, CandleInterval.TEN_MINUTES, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(2L);
        assertThat(candleRepository.findCandlesBefore(
                STOCK_ID, CandleInterval.ONE_HOUR, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void 종목이_다르면_섞이지_않는다() {
        candleRepository.upsertCandle(1L, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(2L, CandleInterval.ONE_MINUTE, NINE, 2L, 2L, 2L, 2L, 2L);

        assertThat(candleRepository.findCandlesBefore(
                2L, CandleInterval.ONE_MINUTE, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(2L);
    }

    @Test
    void 최신순으로_limit개만_조회한다() {
        for (int i = 0; i < 5; i++) {
            candleRepository.upsertCandle(STOCK_ID, CandleInterval.TEN_MINUTES, NINE.plusMinutes(i * 10L),
                    1L, 1L, 1L, 1L, 1L);
        }

        List<StockCandle> found = candleRepository.findCandlesBefore(
                STOCK_ID, CandleInterval.TEN_MINUTES, EPOCH, NINE.plusHours(1), PageRequest.of(0, 2));

        assertThat(found).extracting(StockCandle::getTimestamp)
                .containsExactly(NINE.plusMinutes(40), NINE.plusMinutes(30));
    }

    @Test
    void before는_포함하지_않고_from은_포함한다() {
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(2), 1L, 1L, 1L, 1L, 1L);

        List<StockCandle> found = candleRepository.findCandlesBefore(
                STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), NINE.plusMinutes(2), PageRequest.of(0, 10));

        assertThat(found).extracting(StockCandle::getTimestamp).containsExactly(NINE.plusMinutes(1));
    }

    @Test
    void 구간의_봉을_오래된_순으로_조회한다() {
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(2), 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), 1L, 1L, 1L, 1L, 1L);
        candleRepository.upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(5), 1L, 1L, 1L, 1L, 1L);

        List<StockCandle> found = candleRepository.findCandlesAscending(
                STOCK_ID, CandleInterval.ONE_MINUTE, NINE, NINE.plusMinutes(3));

        assertThat(found).extracting(StockCandle::getTimestamp)
                .containsExactly(NINE, NINE.plusMinutes(1), NINE.plusMinutes(2));
    }
}
