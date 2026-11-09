package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LatestPriceInitializerTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0);
    private static final Instant LAST_TRADE = Instant.parse("2026-10-27T00:00:30Z");

    private StockRepository stockRepository;
    private StockCandleRepository candleRepository;
    private LatestPriceStore store;
    private LatestPriceInitializer initializer;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        candleRepository = mock(StockCandleRepository.class);
        store = new LatestPriceStore();
        initializer = new LatestPriceInitializer(stockRepository, candleRepository, store);
    }

    @Test
    void 저장된_마지막_1분봉의_종가로_최근_체결가를_채운다() {
        Stock stock = stockWithId(1L);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(1L, CandleInterval.ONE_MINUTE))
                .thenReturn(Optional.of(candle(stock, 10_700L, LAST_TRADE.toEpochMilli())));

        initializer.initialize();

        assertThat(store.findPrice(1L)).contains(10_700L);
    }

    @Test
    void 저장된_봉이_없는_종목은_비워_둔다() {
        when(stockRepository.findAll()).thenReturn(List.of(stockWithId(1L)));
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(1L, CandleInterval.ONE_MINUTE))
                .thenReturn(Optional.empty());

        initializer.initialize();

        assertThat(store.findPrice(1L)).isEmpty();
    }

    @Test
    void 복구한_값보다_오래된_체결은_현재가를_되돌리지_못한다() {
        Stock stock = stockWithId(1L);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(1L, CandleInterval.ONE_MINUTE))
                .thenReturn(Optional.of(candle(stock, 10_700L, LAST_TRADE.toEpochMilli())));
        initializer.initialize();

        boolean applied = store.update(1L, 9_000L, LAST_TRADE.minusSeconds(10));

        assertThat(applied).isFalse();
        assertThat(store.findPrice(1L)).contains(10_700L);
    }

    @Test
    void 복구한_값보다_새로운_체결은_현재가를_덮어쓴다() {
        Stock stock = stockWithId(1L);
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(1L, CandleInterval.ONE_MINUTE))
                .thenReturn(Optional.of(candle(stock, 10_700L, LAST_TRADE.toEpochMilli())));
        initializer.initialize();

        store.update(1L, 10_900L, LAST_TRADE.plusSeconds(5));

        assertThat(store.findPrice(1L)).contains(10_900L);
    }

    @Test
    void 조회가_실패해도_예외를_던지지_않는다() {
        when(stockRepository.findAll()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> initializer.initialize()).doesNotThrowAnyException();
    }

    private static StockCandle candle(Stock stock, long close, long lastTradeAt) {
        return new StockCandle(stock, CandleInterval.ONE_MINUTE, NINE, close, close, close, close, 1L,
                lastTradeAt, lastTradeAt);
    }

    private static Stock stockWithId(Long stockId) {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", stockId);
        return stock;
    }
}
