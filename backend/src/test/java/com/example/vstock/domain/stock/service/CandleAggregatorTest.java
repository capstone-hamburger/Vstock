package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.example.vstock.domain.stock.entity.StockCandleId;
import com.example.vstock.domain.stock.event.TradeEvent;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandleAggregatorTest {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    private final Stock stock = stockWithId(STOCK_ID);

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

        verify(candleRepository).upsertCandle(
                STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_100L, 10_000L, 10_100L, 12L,
                millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(20)));
        CandleResponse next = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);
        assertThat(next.time()).isEqualTo(MarketTime.toEpochSecond(NINE.plusMinutes(1)));
        assertThat(next.open()).isEqualTo(10_300);
        assertThat(next.volume()).isEqualTo(2);
    }

    @Test
    void 분이_바뀌어도_10분봉은_저장하지_않고_같은_봉을_이어서_갱신한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        CandleResponse tenMinutes = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.TEN_MINUTES);

        assertThat(tenMinutes.time()).isEqualTo(MarketTime.toEpochSecond(NINE));
        assertThat(tenMinutes.open()).isEqualTo(10_000);
        assertThat(tenMinutes.close()).isEqualTo(10_300);
        assertThat(tenMinutes.volume()).isEqualTo(7);
        verify(candleRepository, never()).upsertCandle(
                any(), eq(CandleInterval.TEN_MINUTES), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 십분_경계를_넘으면_10분봉도_저장한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusMinutes(2)));
        aggregator.onTrade(trade(10_400, 3, NINE.plusMinutes(7)));

        aggregator.onTrade(trade(10_500, 1, NINE.plusMinutes(10).plusSeconds(5)));

        verify(candleRepository).upsertCandle(
                STOCK_ID, CandleInterval.TEN_MINUTES, NINE, 10_000L, 10_400L, 10_000L, 10_400L, 8L,
                millis(NINE.plusMinutes(2)), millis(NINE.plusMinutes(7)));
        verify(candleRepository, never()).upsertCandle(
                any(), eq(CandleInterval.ONE_HOUR), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 마감된_봉은_finalized_true로_발행하고_진행_중_봉은_false로_발행한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        ArgumentCaptor<CandleResponse> captor = ArgumentCaptor.forClass(CandleResponse.class);
        verify(publisher, times(3)).publishCandle(eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), captor.capture());

        assertThat(captor.getAllValues()).extracting(CandleResponse::finalized).containsExactly(false, true, false);
    }

    @Test
    void 다음_체결이_없어도_스케줄러가_지난_분의_봉을_마감한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        clock.set(NINE.plusMinutes(1).plusSeconds(1));
        aggregator.closeExpiredCandles();

        verify(candleRepository).upsertCandle(
                STOCK_ID, CandleInterval.ONE_MINUTE, NINE, 10_000L, 10_000L, 10_000L, 10_000L, 5L,
                millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(10)));
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNull();
        // 10분봉은 아직 구간 안이라 유지된다.
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.TEN_MINUTES)).isNotNull();
    }

    @Test
    void 달이_바뀌면_모든_interval의_봉이_저장된다() {
        LocalDateTime lastMinute = LocalDateTime.of(2026, 10, 31, 23, 59, 30);
        aggregator.onTrade(trade(10_000, 5, lastMinute));

        clock.set(LocalDateTime.of(2026, 11, 1, 0, 0, 1));
        aggregator.closeExpiredCandles();

        for (CandleInterval interval : CandleInterval.values()) {
            verify(candleRepository).upsertCandle(eq(STOCK_ID), eq(interval),
                    eq(interval.bucketStart(lastMinute)), eq(10_000L), eq(10_000L), eq(10_000L), eq(10_000L), eq(5L),
                    eq(millis(lastMinute)), eq(millis(lastMinute)));
            assertThat(aggregator.findCurrentCandle(STOCK_ID, interval)).isNull();
        }
    }

    @Test
    void 스케줄러는_아직_진행_중인_봉을_마감하지_않는다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        clock.set(NINE.plusSeconds(50));
        aggregator.closeExpiredCandles();

        verify(candleRepository, never()).upsertCandle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNotNull();
    }

    @Test
    void 봉_안에서_순서가_뒤바뀐_체결도_체결_시각으로_open과_close를_정한다() {
        aggregator.onTrade(trade(10_000, 1, NINE.plusSeconds(20)));
        aggregator.onTrade(trade(9_000, 2, NINE.plusSeconds(10)));
        aggregator.onTrade(trade(11_000, 3, NINE.plusSeconds(40)));
        aggregator.onTrade(trade(10_500, 4, NINE.plusSeconds(30)));

        CandleResponse candle = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);

        assertThat(candle).extracting(CandleResponse::open, CandleResponse::high, CandleResponse::low,
                CandleResponse::close, CandleResponse::volume).containsExactly(9_000L, 11_000L, 9_000L, 11_000L, 10L);
    }

    @Test
    void 이미_마감된_구간의_늦은_체결은_저장된_봉에_합쳐_다시_저장한다() {
        aggregator.onTrade(trade(10_000, 1, NINE.plusMinutes(1).plusSeconds(5)));
        // 09:00 봉은 이미 저장돼 있다. open 10_000@09:00:10, close 10_200@09:00:50
        when(candleRepository.findById(new StockCandleId(STOCK_ID, CandleInterval.ONE_MINUTE, NINE)))
                .thenReturn(Optional.of(new StockCandle(stock, CandleInterval.ONE_MINUTE, NINE,
                        10_000L, 10_300L, 9_900L, 10_200L, 10L,
                        millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(50)))));

        aggregator.onTrade(trade(20_000, 9, NINE.plusSeconds(30)));

        // 시각이 첫 체결과 마지막 체결 사이라 open·close는 그대로이고 high와 volume만 바뀐다.
        verify(candleRepository).upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE,
                10_000L, 20_000L, 9_900L, 10_200L, 19L, millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(50)));
        // 진행 중인 봉은 영향이 없다.
        CandleResponse live = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE);
        assertThat(live.high()).isEqualTo(10_000);
        assertThat(live.volume()).isEqualTo(1);
    }

    @Test
    void 늦은_체결이_봉의_첫_체결보다_이르면_open이_바뀐다() {
        aggregator.onTrade(trade(10_000, 1, NINE.plusMinutes(1).plusSeconds(5)));
        when(candleRepository.findById(new StockCandleId(STOCK_ID, CandleInterval.ONE_MINUTE, NINE)))
                .thenReturn(Optional.of(new StockCandle(stock, CandleInterval.ONE_MINUTE, NINE,
                        10_000L, 10_300L, 9_900L, 10_200L, 10L,
                        millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(50)))));

        aggregator.onTrade(trade(9_500, 2, NINE.plusSeconds(5)));

        verify(candleRepository).upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE,
                9_500L, 10_300L, 9_500L, 10_200L, 12L, millis(NINE.plusSeconds(5)), millis(NINE.plusSeconds(50)));
    }

    @Test
    void 늦은_체결이_봉의_마지막_체결보다_늦으면_close가_바뀐다() {
        aggregator.onTrade(trade(10_000, 1, NINE.plusMinutes(1).plusSeconds(5)));
        when(candleRepository.findById(new StockCandleId(STOCK_ID, CandleInterval.ONE_MINUTE, NINE)))
                .thenReturn(Optional.of(new StockCandle(stock, CandleInterval.ONE_MINUTE, NINE,
                        10_000L, 10_300L, 9_900L, 10_200L, 10L,
                        millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(50)))));

        aggregator.onTrade(trade(10_400, 2, NINE.plusSeconds(59)));

        verify(candleRepository).upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE,
                10_000L, 10_400L, 9_900L, 10_400L, 12L, millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(59)));
    }

    @Test
    void 늦은_체결의_구간이_저장돼_있지_않으면_그_체결로_새_봉을_만든다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusMinutes(1).plusSeconds(5)));

        aggregator.onTrade(trade(20_000, 9, NINE.plusSeconds(30)));

        verify(candleRepository).upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE,
                20_000L, 20_000L, 20_000L, 20_000L, 9L, millis(NINE.plusSeconds(30)), millis(NINE.plusSeconds(30)));
    }

    @Test
    void 늦은_체결로_고친_봉은_마감된_봉으로_다시_발행한다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusMinutes(1).plusSeconds(5)));

        aggregator.onTrade(trade(20_000, 9, NINE.plusSeconds(30)));

        ArgumentCaptor<CandleResponse> captor = ArgumentCaptor.forClass(CandleResponse.class);
        verify(publisher, times(2)).publishCandle(eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), captor.capture());
        CandleResponse corrected = captor.getAllValues().get(1);
        assertThat(corrected.finalized()).isTrue();
        assertThat(corrected.time()).isEqualTo(MarketTime.toEpochSecond(NINE));
        assertThat(corrected.volume()).isEqualTo(9);
    }

    @Test
    void 스케줄러가_이미_닫은_구간의_늦은_체결도_저장된_봉에_합친다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));
        clock.set(NINE.plusMinutes(1).plusSeconds(1));
        aggregator.closeExpiredCandles();
        when(candleRepository.findById(new StockCandleId(STOCK_ID, CandleInterval.ONE_MINUTE, NINE)))
                .thenReturn(Optional.of(new StockCandle(stock, CandleInterval.ONE_MINUTE, NINE,
                        10_000L, 10_000L, 10_000L, 10_000L, 5L,
                        millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(10)))));

        aggregator.onTrade(trade(10_700, 2, NINE.plusSeconds(55)));

        verify(candleRepository).upsertCandle(STOCK_ID, CandleInterval.ONE_MINUTE, NINE,
                10_000L, 10_700L, 10_000L, 10_700L, 7L, millis(NINE.plusSeconds(10)), millis(NINE.plusSeconds(55)));
        // 이미 닫힌 구간이므로 진행 중인 봉으로 되살아나지 않는다.
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNull();
    }

    @Test
    void 늦은_체결을_반영하다_저장이_실패해도_예외를_던지지_않는다() {
        aggregator.onTrade(trade(10_000, 5, NINE.plusMinutes(1).plusSeconds(5)));
        when(candleRepository.findById(any())).thenThrow(new RuntimeException("db down"));

        aggregator.onTrade(trade(20_000, 9, NINE.plusSeconds(30)));

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE).volume()).isEqualTo(5);
    }

    @Test
    void 저장이_실패해도_체결_처리는_계속된다() {
        doThrow(new RuntimeException("db down")).when(candleRepository)
                .upsertCandle(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        aggregator.onTrade(trade(10_000, 5, NINE.plusSeconds(10)));

        aggregator.onTrade(trade(10_300, 2, NINE.plusMinutes(1).plusSeconds(5)));

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE).open()).isEqualTo(10_300);
    }

    @Test
    void 서버_시작_시_한_단계_낮은_간격의_봉으로_진행_중인_봉을_차례로_복구한다() {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", STOCK_ID);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        clock.set(NINE.plusMinutes(44).plusSeconds(20));
        LocalDateTime now = LocalDateTime.now(clock);

        // 10분봉 09:40은 저장된 1분봉 09:40~09:43으로 채운다.
        when(candleRepository.findCandlesAscending(STOCK_ID, CandleInterval.ONE_MINUTE, NINE.plusMinutes(40), now))
                .thenReturn(List.of(
                        stored(CandleInterval.ONE_MINUTE, NINE.plusMinutes(40), 100, 103, 99, 102, 10),
                        stored(CandleInterval.ONE_MINUTE, NINE.plusMinutes(41), 102, 105, 101, 104, 8),
                        stored(CandleInterval.ONE_MINUTE, NINE.plusMinutes(42), 104, 104, 100, 101, 12),
                        stored(CandleInterval.ONE_MINUTE, NINE.plusMinutes(43), 101, 102, 98, 99, 15)));
        // 1시간봉 09:00은 저장된 10분봉 09:00~09:30 + 방금 복구한 10분봉 09:40으로 채운다.
        when(candleRepository.findCandlesAscending(STOCK_ID, CandleInterval.TEN_MINUTES, NINE, now))
                .thenReturn(List.of(
                        stored(CandleInterval.TEN_MINUTES, NINE, 90, 95, 88, 93, 100),
                        stored(CandleInterval.TEN_MINUTES, NINE.plusMinutes(10), 93, 99, 92, 98, 80),
                        stored(CandleInterval.TEN_MINUTES, NINE.plusMinutes(20), 98, 98, 90, 91, 60),
                        stored(CandleInterval.TEN_MINUTES, NINE.plusMinutes(30), 91, 96, 91, 95, 70)));

        aggregator.restore();

        CandleResponse tenMinutes = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.TEN_MINUTES);
        assertThat(tenMinutes.time()).isEqualTo(MarketTime.toEpochSecond(NINE.plusMinutes(40)));
        assertThat(tenMinutes).extracting(CandleResponse::open, CandleResponse::high, CandleResponse::low,
                CandleResponse::close, CandleResponse::volume).containsExactly(100L, 105L, 98L, 99L, 45L);

        CandleResponse oneHour = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_HOUR);
        assertThat(oneHour.time()).isEqualTo(MarketTime.toEpochSecond(NINE));
        assertThat(oneHour).extracting(CandleResponse::open, CandleResponse::high, CandleResponse::low,
                CandleResponse::close, CandleResponse::volume).containsExactly(90L, 105L, 88L, 99L, 355L);

        // 일봉과 달봉은 저장된 낮은 봉이 없어도, 복구된 낮은 간격의 진행 중인 봉으로 이어서 채워진다.
        CandleResponse day = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_DAY);
        assertThat(day.time()).isEqualTo(MarketTime.toEpochSecond(NINE.toLocalDate().atStartOfDay()));
        assertThat(day.volume()).isEqualTo(355L);
        CandleResponse month = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MONTH);
        assertThat(month.time()).isEqualTo(MarketTime.toEpochSecond(LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertThat(month.volume()).isEqualTo(355L);

        // 1분봉은 복구할 수 없다.
        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).isNull();
    }

    @Test
    void 복구한_봉은_다음_체결로_이어서_갱신된다() {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", STOCK_ID);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        clock.set(NINE.plusMinutes(5));
        when(candleRepository.findCandlesAscending(
                eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), eq(NINE), any()))
                .thenReturn(List.of(stored(CandleInterval.ONE_MINUTE, NINE, 100, 103, 99, 102, 10)));
        aggregator.restore();

        aggregator.onTrade(trade(110, 5, NINE.plusMinutes(5).plusSeconds(5)));

        CandleResponse tenMinutes = aggregator.findCurrentCandle(STOCK_ID, CandleInterval.TEN_MINUTES);
        assertThat(tenMinutes).extracting(CandleResponse::open, CandleResponse::high, CandleResponse::low,
                CandleResponse::close, CandleResponse::volume).containsExactly(100L, 110L, 99L, 110L, 15L);
    }

    @Test
    void 복구에_실패해도_예외를_밖으로_던지지_않는다() {
        when(stockRepository.findAll()).thenThrow(new RuntimeException("db down"));

        aggregator.restore();

        assertThat(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.TEN_MINUTES)).isNull();
    }

    private StockCandle stored(CandleInterval interval, LocalDateTime start,
                               long open, long high, long low, long close, long volume) {
        return new StockCandle(stock, interval, start, open, high, low, close, volume, millis(start), millis(start));
    }

    private static long millis(LocalDateTime time) {
        return time.atZone(MarketTime.ZONE).toInstant().toEpochMilli();
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

    private static Stock stockWithId(Long stockId) {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", stockId);
        return stock;
    }
}
