package com.example.vstock.domain.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.domain.trade.repository.TradeRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BotTradePurgerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0, 30);
    private static final long RETENTION_MINUTES = 5;

    private StockRepository stockRepository;
    private StockCandleRepository candleRepository;
    private TradeRepository tradeRepository;
    private BotTradePurger purger;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        candleRepository = mock(StockCandleRepository.class);
        tradeRepository = mock(TradeRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(MarketTime.ZONE).toInstant(), MarketTime.ZONE);
        purger = new BotTradePurger(stockRepository, candleRepository, tradeRepository, clock, RETENTION_MINUTES);
    }

    @Test
    void 저장된_마지막_1분봉의_끝이_보관_기준보다_이르면_봉의_끝까지만_지운다() {
        // 서버가 한동안 꺼져 있었다: 마지막 저장 봉이 09:30이면 09:31 이후 체결은 봉이 되지 못했으니 남긴다.
        givenStoredLastBar(1L, LocalDateTime.of(2026, 10, 27, 9, 30));

        assertThat(purger.findCutoff(1L, NOW)).contains(LocalDateTime.of(2026, 10, 27, 9, 31));
    }

    @Test
    void 보관_기준이_봉의_끝보다_이르면_보관_기준까지만_지운다() {
        // 마지막 저장 봉이 방금(09:59) 닫혔어도 최근 5분은 남겨, 중간 봉 저장 실패에 대비한다.
        givenStoredLastBar(1L, LocalDateTime.of(2026, 10, 27, 9, 59));

        assertThat(purger.findCutoff(1L, NOW)).contains(NOW.minusMinutes(RETENTION_MINUTES));
    }

    @Test
    void 저장된_1분봉이_없으면_지우지_않는다() {
        when(stockRepository.findAll()).thenReturn(List.of(stockWithId(1L)));
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(
                1L, CandleInterval.ONE_MINUTE)).thenReturn(Optional.empty());

        int deleted = purger.purge();

        assertThat(deleted).isZero();
        verify(tradeRepository, never()).deleteBotOnlyTradesBefore(any(), any());
    }

    @Test
    void 종목마다_기준_시각을_따로_계산해_지우고_합계를_돌려준다() {
        when(stockRepository.findAll()).thenReturn(List.of(stockWithId(1L), stockWithId(2L)));
        givenStoredLastBar(1L, LocalDateTime.of(2026, 10, 27, 9, 30));
        givenStoredLastBar(2L, LocalDateTime.of(2026, 10, 27, 9, 59));
        when(tradeRepository.deleteBotOnlyTradesBefore(eq(1L), any())).thenReturn(4);
        when(tradeRepository.deleteBotOnlyTradesBefore(eq(2L), any())).thenReturn(2);

        int deleted = purger.purge();

        assertThat(deleted).isEqualTo(6);
        verify(tradeRepository).deleteBotOnlyTradesBefore(1L, LocalDateTime.of(2026, 10, 27, 9, 31));
        verify(tradeRepository).deleteBotOnlyTradesBefore(2L, NOW.minusMinutes(RETENTION_MINUTES));
    }

    @Test
    void 한_종목이_실패해도_나머지_종목은_계속_지운다() {
        when(stockRepository.findAll()).thenReturn(List.of(stockWithId(1L), stockWithId(2L)));
        givenStoredLastBar(1L, LocalDateTime.of(2026, 10, 27, 9, 30));
        givenStoredLastBar(2L, LocalDateTime.of(2026, 10, 27, 9, 30));
        when(tradeRepository.deleteBotOnlyTradesBefore(eq(1L), any())).thenThrow(new IllegalStateException("db down"));
        when(tradeRepository.deleteBotOnlyTradesBefore(eq(2L), any())).thenReturn(3);

        assertThat(purger.purge()).isEqualTo(3);
    }

    @Test
    void 보관_시간이_1분_미만이면_만들_수_없다() {
        Clock clock = Clock.systemUTC();

        assertThatThrownBy(() -> new BotTradePurger(stockRepository, candleRepository, tradeRepository, clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenStoredLastBar(Long stockId, LocalDateTime barStart) {
        StockCandle candle = new StockCandle(stockWithId(stockId), CandleInterval.ONE_MINUTE, barStart,
                1L, 1L, 1L, 1L, 1L, 0L, 0L);
        when(candleRepository.findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(
                stockId, CandleInterval.ONE_MINUTE)).thenReturn(Optional.of(candle));
    }

    private static Stock stockWithId(Long stockId) {
        Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);
        ReflectionTestUtils.setField(stock, "stockId", stockId);
        return stock;
    }
}
