package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.event.TradeEvent;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandleAggregatorTest {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    private StockCandleRepository candleRepository;
    private StockRepository stockRepository;
    private StockMessagePublisher publisher;
    private MutableClock clock;
    private CandleAggregator aggregator;

    @BeforeEach
    void setUp() {
        candleRepository = mock(StockCandleRepository.class);
        stockRepository = mock(StockRepository.class);
        publisher = mock(StockMessagePublisher.class);
        clock = new MutableClock(NINE);
        aggregator = new CandleAggregator(candleRepository, stockRepository, publisher, clock);
    }

    @Test
    void 첫_체결로_모든_interval의_진행_중인_봉이_만들어진다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        for (CandleInterval interval : CandleInterval.values()) {
            CandleResponse candle = aggregator.findCurrentCandle(STOCK_ID, interval);
            assertThat(candle.open()).isEqualTo(10_000);
            assertThat(candle.high()).isEqualTo(10_000);
            assertThat(candle.low()).isEqualTo(10_000);
            assertThat(candle.close()).isEqualTo(10_000);
            assertThat(candle.volume()).isEqualTo(5);
            assertThat(candle.finalized()).isFalse();
        }
    }

    @Test
    void 같은_분의_체결은_high_low_close_volume을_갱신한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_200, 3, NINE.plusSeconds(20)));
        aggregator.onTrade(trade(9_900, 2, NINE.plusSeconds(30)));
        aggregator.onTrade(trade(10_100, 1, NINE.plusSeconds(40)));

        CandleResponse candle = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);

        assertThat(candle.open()).isEqualTo(10_000);
        assertThat(candle.high()).isEqualTo(10_200);
        assertThat(candle.low()).isEqualTo(9_900);
        assertThat(candle.close()).isEqualTo(10_100);
        assertThat(candle.volume()).isEqualTo(11);
    }

    @Test
    void 체결마다_seq가_증가한다() {
        aggregator.onTrade(trade(10_000, 1, NINE.plusSeconds(1)));
        aggregator.onTrade(trade(10_000, 1, NINE.plusSeconds(2)));

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE).seq()).isEqualTo(2);
    }

    @Test
    void 분이_바뀌면_이전_1분봉을_저장하고_새_봉을_만든다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_100, 7, NINE.plusSeconds(20)));

        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        verify(candleRepository).upsertCandle(STOCK_ID, NINE, 10_000L, 10_100L, 10_000L, 10_100L, 12L);
        CandleResponse next = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);
        assertThat(next.time()).isEqualTo(MarketTime.toEpochSecond(NINE.plusMinutes(1)));
        assertThat(next.open()).isEqualTo(10_300);
        assertThat(next.volume()).isEqualTo(2);
    }

    @Test
    void 분이_바뀌어도_5분봉은_같은_봉을_이어서_갱신한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        CandleResponse fiveMinutes = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.FIVE_MINUTES);

        assertThat(fiveMinutes.time()).isEqualTo(MarketTime.toEpochSecond(NINE));
        assertThat(fiveMinutes.open()).isEqualTo(10_000);
        assertThat(fiveMinutes.close()).isEqualTo(10_300);
        assertThat(fiveMinutes.volume()).isEqualTo(7);
    }

    @Test
    void 마감된_봉은_finalized_true로_발행하고_진행_중_봉은_false로_발행한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        ArgumentCaptor<CandleResponse> captor = ArgumentCaptor.forClass(CandleResponse.class);
        verify(publisher, times(3)).publishCandle(eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), captor.capture());
        List<CandleResponse> messages = captor.getAllValues();

        assertThat(messages).extracting(CandleResponse::finalized).containsExactly(false, true, false);
    }

    @Test
    void 다음_체결이_없어도_스케줄러가_지난_분의_봉을_마감한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        clock.set(NINE.plusMinutes(1).plusSeconds(1));
        aggregator.closeExpiredCandles();

        verify(candleRepository).upsertCandle(STOCK_ID, NINE, 10_000L, 10_000L, 10_000L, 10_000L, 5L);
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNull();
        // 5분봉은 아직 구간 안이라 유지된다.
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.FIVE_MINUTES)).isNotNull();
    }

    @Test
    void 스케줄러는_아직_진행_중인_봉을_마감하지_않는다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        clock.set(NINE.plusSeconds(50));
        aggregator.closeExpiredCandles();

        verify(candleRepository, never()).upsertCandle(any(), any(), any(), any(), any(), any(), any());
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNotNull();
    }

    @Test
    void 이미_지난_구간의_체결은_무시한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusMinutes(1).plusSeconds(5)));

        aggregator.onTrade(trade(20_000, 9, NINE.plusSeconds(30)));

        CandleResponse candle = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);
        assertThat(candle.high()).isEqualTo(10_000);
        assertThat(candle.volume()).isEqualTo(5);
    }

    @Test
    void 저장이_실패해도_체결_처리는_계속된다() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(candleRepository).upsertCandle(any(), any(), any(), any(), any(), any(), any());
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE).open()).isEqualTo(10_300);
    }

    @Test
    void 서버_시작_시_저장된_1분봉으로_상위_봉을_복구한다() {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", STOCK_ID);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        clock.set(NINE.plusMinutes(3).plusSeconds(20));
        when(candleRepository.findCandlesAscending(eq(STOCK_ID), eq(NINE), any())).thenReturn(List.of(
                new StockCandle(STOCK_ID, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L),
                new StockCandle(STOCK_ID, NINE.plusMinutes(1), 10_100L, 10_500L, 10_000L, 10_400L, 20L),
                new StockCandle(STOCK_ID, NINE.plusMinutes(2), 10_400L, 10_450L, 9_800L, 9_900L, 5L)));

        aggregator.restore();

        CandleResponse fiveMinutes = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.FIVE_MINUTES);
        assertThat(fiveMinutes.open()).isEqualTo(10_000);
        assertThat(fiveMinutes.high()).isEqualTo(10_500);
        assertThat(fiveMinutes.low()).isEqualTo(9_800);
        assertThat(fiveMinutes.close()).isEqualTo(9_900);
        assertThat(fiveMinutes.volume()).isEqualTo(35);
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNull();
    }

    @Test
    void 복구에_실패해도_예외를_밖으로_던지지_않는다() {
        when(stockRepository.findAll()).thenThrow(new RuntimeException("db down"));

        aggregator.restore();

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.FIVE_MINUTES)).isNull();
    }

    private TradeEvent trade(long price, long quantity, LocalDateTime executedAt) {
        return new TradeEvent(STOCK_ID, price, quantity, executedAt.atZone(MarketTime.ZONE).toInstant());
    }

    /** 테스트에서 시각을 옮길 수 있는 Clock. */
    private static class MutableClock extends Clock {

        private Instant now;

        MutableClock(LocalDateTime time) {
            set(time);
        }

        void set(LocalDateTime time) {
            this.now = time.atZone(MarketTime.ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return MarketTime.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
