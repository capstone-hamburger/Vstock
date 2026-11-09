package com.example.vstock.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.support.RepositoryTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class StockCandleRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0);

    @Autowired
    private StockCandleRepository candleRepository;

    private Long stockId;
    private Long otherStockId;

    // stock_candles는 stocks를 참조하므로 봉을 저장하기 전에 종목이 있어야 한다.
    @BeforeEach
    void saveStocks() {
        stockId = em.persistAndGetId(new Stock("테스트종목", Sector.IT, 10_000L), Long.class);
        otherStockId = em.persistAndGetId(new Stock("다른종목", Sector.FINANCE, 20_000L), Long.class);
    }

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
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_500L, 9_900L, 10_400L, 25L, 1_000L, 2_000L);
        flushAndClear();

        List<StockCandle> found = candleRepository.findCandlesAscending(
                stockId, CandleInterval.ONE_MINUTE, NINE, NINE.plusMinutes(1));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getCandleInterval()).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(found.get(0).getHighPrice()).isEqualTo(10_500L);
        assertThat(found.get(0).getVolume()).isEqualTo(25L);
        assertThat(found.get(0).getFirstTradeAt()).isEqualTo(1_000L);
        assertThat(found.get(0).getLastTradeAt()).isEqualTo(2_000L);
    }

    @Test
    void 시작_시각이_같아도_interval이_다르면_따로_저장된다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.TEN_MINUTES, NINE, 2L, 2L, 2L, 2L, 2L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MONTH, NINE, 3L, 3L, 3L, 3L, 3L, 0L, 0L);
        flushAndClear();

        assertThat(candleRepository.findCandlesBefore(
                stockId, CandleInterval.ONE_MINUTE, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(1L);
        assertThat(candleRepository.findCandlesBefore(
                stockId, CandleInterval.TEN_MINUTES, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(2L);
        assertThat(candleRepository.findCandlesBefore(
                stockId, CandleInterval.ONE_HOUR, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void 종목이_다르면_섞이지_않는다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(otherStockId, CandleInterval.ONE_MINUTE, NINE, 2L, 2L, 2L, 2L, 2L, 0L, 0L);

        assertThat(candleRepository.findCandlesBefore(
                otherStockId, CandleInterval.ONE_MINUTE, EPOCH, NINE.plusDays(1), PageRequest.of(0, 10)))
                .extracting(StockCandle::getOpenPrice).containsExactly(2L);
    }

    @Test
    void 최신순으로_limit개만_조회한다() {
        for (int i = 0; i < 5; i++) {
            candleRepository.upsertCandle(stockId, CandleInterval.TEN_MINUTES, NINE.plusMinutes(i * 10L),
                    1L, 1L, 1L, 1L, 1L, 0L, 0L);
        }

        List<StockCandle> found = candleRepository.findCandlesBefore(
                stockId, CandleInterval.TEN_MINUTES, EPOCH, NINE.plusHours(1), PageRequest.of(0, 2));

        assertThat(found).extracting(StockCandle::getTimestamp)
                .containsExactly(NINE.plusMinutes(40), NINE.plusMinutes(30));
    }

    @Test
    void before는_포함하지_않고_from은_포함한다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(2), 1L, 1L, 1L, 1L, 1L, 0L, 0L);

        List<StockCandle> found = candleRepository.findCandlesBefore(
                stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), NINE.plusMinutes(2), PageRequest.of(0, 10));

        assertThat(found).extracting(StockCandle::getTimestamp).containsExactly(NINE.plusMinutes(1));
    }

    @Test
    void 구간의_봉을_오래된_순으로_조회한다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(2), 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(5), 1L, 1L, 1L, 1L, 1L, 0L, 0L);

        List<StockCandle> found = candleRepository.findCandlesAscending(
                stockId, CandleInterval.ONE_MINUTE, NINE, NINE.plusMinutes(3));

        assertThat(found).extracting(StockCandle::getTimestamp)
                .containsExactly(NINE, NINE.plusMinutes(1), NINE.plusMinutes(2));
    }

    @Test
    void 간격과_종목이_같은_봉_중_가장_최근_하나를_조회한다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(2), 3L, 3L, 3L, 3L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(1), 2L, 2L, 2L, 2L, 1L, 0L, 0L);
        candleRepository.upsertCandle(stockId, CandleInterval.TEN_MINUTES, NINE.plusMinutes(10), 9L, 9L, 9L, 9L, 1L, 0L, 0L);
        candleRepository.upsertCandle(otherStockId, CandleInterval.ONE_MINUTE, NINE.plusMinutes(30), 8L, 8L, 8L, 8L, 1L, 0L, 0L);
        flushAndClear();

        assertThat(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(
                stockId, CandleInterval.ONE_MINUTE))
                .get().extracting(StockCandle::getClosePrice).isEqualTo(3L);
        assertThat(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(
                stockId, CandleInterval.ONE_HOUR)).isEmpty();
    }

    @Test
    void 없는_종목의_봉은_외래키_제약으로_저장할_수_없다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            candleRepository.upsertCandle(stockId + 1_000L, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void 봉에서_종목을_객체로_접근할_수_있다() {
        candleRepository.upsertCandle(stockId, CandleInterval.ONE_MINUTE, NINE, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
        flushAndClear();

        StockCandle found = candleRepository.findCandlesAscending(
                stockId, CandleInterval.ONE_MINUTE, NINE, NINE.plusMinutes(1)).get(0);

        assertThat(found.getStock().getStockId()).isEqualTo(stockId);
    }
}
