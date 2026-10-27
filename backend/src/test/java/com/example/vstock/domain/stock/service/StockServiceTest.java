package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StockServiceTest {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    private StockRepository stockRepository;
    private StockCandleRepository candleRepository;
    private CandleAggregator aggregator;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        candleRepository = mock(StockCandleRepository.class);
        aggregator = mock(CandleAggregator.class);
        Clock clock = Clock.fixed(NINE.plusMinutes(3).atZone(MarketTime.ZONE).toInstant(), MarketTime.ZONE);
        stockService = new StockService(stockRepository, candleRepository, aggregator, clock);
        when(stockRepository.existsById(STOCK_ID)).thenReturn(true);
    }

    @Test
    void 존재하지_않는_종목이면_404를_던진다() {
        when(stockRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> stockService.findCandles(99L, "1m", null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void 지원하지_않는_interval이면_400을_던진다() {
        assertThatThrownBy(() -> stockService.findCandles(STOCK_ID, "1y", null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void limit이_범위를_벗어나면_400을_던진다() {
        assertThatThrownBy(() -> stockService.findCandles(STOCK_ID, "1m", null, null, 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> stockService.findCandles(STOCK_ID, "1m", null, null,
                StockService.MAX_CANDLE_LIMIT + 1))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void from이_to보다_늦으면_400을_던진다() {
        var from = NINE.plusHours(1).atZone(MarketTime.ZONE).toOffsetDateTime();
        var to = NINE.atZone(MarketTime.ZONE).toOffsetDateTime();

        assertThatThrownBy(() -> stockService.findCandles(STOCK_ID, "1m", from, to, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 분봉은_저장된_봉을_오름차순으로_돌려주고_진행_중인_봉을_마지막에_붙인다() {
        // 레포지토리는 최신순으로 돌려준다
        when(candleRepository.findLatestCandles(eq(STOCK_ID), any(), any(), any())).thenReturn(List.of(
                new StockCandle(STOCK_ID, NINE.plusMinutes(1), 10_100L, 10_300L, 10_000L, 10_200L, 20L),
                new StockCandle(STOCK_ID, NINE, 10_000L, 10_200L, 9_900L, 10_100L, 10L)));
        CandleResponse live = new CandleResponse(
                MarketTime.toEpochSecond(NINE.plusMinutes(2)), 10_200, 10_250, 10_150, 10_250, 4, false, 17);
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).thenReturn(live);

        List<CandleResponse> candles = stockService.findCandles(STOCK_ID, "1m", null, null, null);

        assertThat(candles).extracting(CandleResponse::time).containsExactly(
                MarketTime.toEpochSecond(NINE),
                MarketTime.toEpochSecond(NINE.plusMinutes(1)),
                MarketTime.toEpochSecond(NINE.plusMinutes(2)));
        assertThat(candles.get(0).finalized()).isTrue();
        assertThat(candles.get(2)).isEqualTo(live);
    }

    @Test
    void 진행_중인_봉은_같은_시작_시각의_집계_결과를_교체한다() {
        Object[] aggregated = {NINE.toEpochSecond(ZoneOffset.UTC), 10_000L, 10_200L, 9_900L, 10_100L, new java.math.BigDecimal(30)};
        List<Object[]> rows = new ArrayList<>();
        rows.add(aggregated);
        when(candleRepository.findAggregatedCandles(eq(STOCK_ID), any(), any(), eq(300), anyInt())).thenReturn(rows);
        CandleResponse live = new CandleResponse(
                MarketTime.toEpochSecond(NINE), 10_000, 10_300, 9_900, 10_300, 34, false, 5);
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.FIVE_MINUTES)).thenReturn(live);

        List<CandleResponse> candles = stockService.findCandles(STOCK_ID, "5m", null, null, null);

        assertThat(candles).containsExactly(live);
    }

    @Test
    void 조회_구간_밖의_진행_중인_봉은_붙이지_않는다() {
        var to = NINE.plusMinutes(1).atZone(MarketTime.ZONE).toOffsetDateTime();
        CandleResponse live = new CandleResponse(
                MarketTime.toEpochSecond(NINE.plusMinutes(2)), 1, 1, 1, 1, 1, false, 1);
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).thenReturn(live);

        List<CandleResponse> candles = stockService.findCandles(STOCK_ID, "1m", null, to, null);

        assertThat(candles).isEmpty();
    }

    @Test
    void 집계_결과는_저장된_봉과_같은_형태로_변환된다() {
        Object[] aggregated = {NINE.toEpochSecond(ZoneOffset.UTC), 10_000L, 10_200L, 9_900L, 10_100L, new java.math.BigDecimal(30)};
        List<Object[]> rows = new ArrayList<>();
        rows.add(aggregated);
        when(candleRepository.findAggregatedCandles(eq(STOCK_ID), any(), any(), eq(86_400), anyInt())).thenReturn(rows);

        List<CandleResponse> candles = stockService.findCandles(STOCK_ID, "1d", null, null, null);

        assertThat(candles).containsExactly(new CandleResponse(
                MarketTime.toEpochSecond(NINE), 10_000, 10_200, 9_900, 10_100, 30, true, 0));
    }
}
