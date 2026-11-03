package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.dto.FindCandleListResponse;
import com.example.vstock.domain.stock.dto.StockResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StockServiceTest {

    private static final Long STOCK_ID = 1L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    private StockRepository stockRepository;
    private LatestPriceStore latestPriceStore;
    private StockCandleRepository candleRepository;
    private CandleAggregator aggregator;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        latestPriceStore = new LatestPriceStore();
        candleRepository = mock(StockCandleRepository.class);
        aggregator = mock(CandleAggregator.class);
        Clock clock = Clock.fixed(NINE.plusMinutes(3).atZone(MarketTime.ZONE).toInstant(), MarketTime.ZONE);
        stockService = new StockService(stockRepository, latestPriceStore, candleRepository, aggregator, clock);
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
    void from이_before보다_늦으면_400을_던진다() {
        assertThatThrownBy(() -> stockService.findCandles(STOCK_ID, "1m", 2_000L, 1_000L, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 요청한_interval의_저장된_봉을_오름차순으로_돌려주고_진행_중인_봉을_마지막에_붙인다() {
        // 레포지토리는 최신순으로 돌려준다
        when(candleRepository.findCandlesBefore(eq(STOCK_ID), eq(CandleInterval.ONE_HOUR), any(), any(), any()))
                .thenReturn(List.of(stored(CandleInterval.ONE_HOUR, NINE.minusHours(1)),
                        stored(CandleInterval.ONE_HOUR, NINE.minusHours(2))));
        CandleResponse live = live(NINE);
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_HOUR)).thenReturn(live);

        FindCandleListResponse response = stockService.findCandles(STOCK_ID, "1h", null, null, null);

        assertThat(response.interval()).isEqualTo("1h");
        assertThat(response.candles()).extracting(CandleResponse::time).containsExactly(
                MarketTime.toEpochSecond(NINE.minusHours(2)),
                MarketTime.toEpochSecond(NINE.minusHours(1)),
                MarketTime.toEpochSecond(NINE));
        assertThat(response.candles().get(0).finalized()).isTrue();
        assertThat(response.candles().get(2)).isEqualTo(live);
        assertThat(response.nextBefore()).isNull();
    }

    @Test
    void 달봉도_같은_방식으로_저장된_봉을_읽는다() {
        when(candleRepository.findCandlesBefore(eq(STOCK_ID), eq(CandleInterval.ONE_MONTH), any(), any(), any()))
                .thenReturn(List.of(stored(CandleInterval.ONE_MONTH, LocalDateTime.of(2026, 9, 1, 0, 0))));

        FindCandleListResponse response = stockService.findCandles(STOCK_ID, "1M", null, null, null);

        assertThat(response.interval()).isEqualTo("1M");
        assertThat(response.candles()).hasSize(1);
    }

    @Test
    void 더_과거의_봉이_남아_있으면_nextBefore로_가장_오래된_봉의_시각을_준다() {
        when(candleRepository.findCandlesBefore(eq(STOCK_ID), eq(CandleInterval.TEN_MINUTES), any(), any(), any()))
                .thenReturn(List.of(stored(CandleInterval.TEN_MINUTES, NINE.plusMinutes(20)),
                        stored(CandleInterval.TEN_MINUTES, NINE.plusMinutes(10)),
                        stored(CandleInterval.TEN_MINUTES, NINE)));

        FindCandleListResponse response = stockService.findCandles(STOCK_ID, "10m", null, null, 2);

        assertThat(response.candles()).extracting(CandleResponse::time).containsExactly(
                MarketTime.toEpochSecond(NINE.plusMinutes(10)), MarketTime.toEpochSecond(NINE.plusMinutes(20)));
        assertThat(response.nextBefore()).isEqualTo(MarketTime.toEpochSecond(NINE.plusMinutes(10)));
    }

    @Test
    void 레포지토리에는_limit보다_한_개_더_요청해_남은_봉을_확인한다() {
        stockService.findCandles(STOCK_ID, "1m", null, null, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(candleRepository).findCandlesBefore(eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), any(), any(),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(6);
    }

    @Test
    void 진행_중인_봉을_붙여도_limit을_넘지_않는다() {
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).thenReturn(live(NINE));

        stockService.findCandles(STOCK_ID, "1m", null, null, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(candleRepository).findCandlesBefore(eq(STOCK_ID), eq(CandleInterval.ONE_MINUTE), any(), any(),
                pageable.capture());
        // 저장된 봉은 4개 + 남았는지 확인용 1개
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void 과거_구간을_조회하면_진행_중인_봉을_붙이지_않는다() {
        when(aggregator.findCurrentCandle(STOCK_ID, CandleInterval.ONE_MINUTE)).thenReturn(live(NINE.plusMinutes(2)));
        long before = MarketTime.toEpochSecond(NINE.plusMinutes(1));

        FindCandleListResponse response = stockService.findCandles(STOCK_ID, "1m", null, before, null);

        assertThat(response.candles()).isEmpty();
    }

    @Test
    void 저장된_봉이_하나도_없고_진행_중인_봉도_없으면_빈_목록이다() {
        FindCandleListResponse response = stockService.findCandles(STOCK_ID, "1d", null, null, null);

        assertThat(response.candles()).isEmpty();
        assertThat(response.nextBefore()).isNull();
    }

    private StockCandle stored(CandleInterval interval, LocalDateTime start) {
        return new StockCandle(STOCK_ID, interval, start, 10_000L, 10_200L, 9_900L, 10_100L, 10L);
    }

    private CandleResponse live(LocalDateTime start) {
        return new CandleResponse(MarketTime.toEpochSecond(start), 10_200, 10_250, 10_150, 10_250, 4, false, 17);
    }

    @Test
    void 체결이_없으면_DB에_저장된_현재가를_돌려준다() {
        stubStock(1L);

        StockResponse response = stockService.findStockById(1L);

        assertThat(response.stockId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("테스트종목");
        assertThat(response.sector()).isEqualTo(Sector.IT);
        assertThat(response.currentPrice()).isEqualTo(10_000L);
    }

    @Test
    void 최근_체결가가_있으면_DB_값보다_우선한다() {
        stubStock(1L);
        latestPriceStore.update(1L, 10_700L, Instant.parse("2026-10-27T00:00:00Z"));

        assertThat(stockService.findStockById(1L).currentPrice()).isEqualTo(10_700L);
    }

    @Test
    void 종목_조회시_존재하지_않는_종목이면_404를_던진다() {
        when(stockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.findStockById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private void stubStock(Long stockId) {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", stockId);
        when(stockRepository.findById(stockId)).thenReturn(Optional.of(stock));
    }
}
