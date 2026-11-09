package com.example.vstock.domain.stock.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.example.vstock.domain.stock.dto.PriceResponse;
import com.example.vstock.domain.stock.event.TradeEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockPriceListenerTest {

    private static final Instant T0 = Instant.parse("2026-10-27T00:00:00Z");

    private LatestPriceStore store;
    private StockMessagePublisher publisher;
    private StockPriceListener listener;

    @BeforeEach
    void setUp() {
        store = new LatestPriceStore();
        publisher = mock(StockMessagePublisher.class);
        listener = new StockPriceListener(store, publisher);
    }

    @Test
    void 체결이_들어오면_현재가를_갱신하고_구독자에게_발행한다() {
        listener.onTrade(new TradeEvent(1L, 10_300L, 7L, T0));

        assertThat(store.findPrice(1L)).contains(10_300L);
        verify(publisher).publishPrice(1L, new PriceResponse(1L, 10_300L, 7L, T0.toEpochMilli()));
    }

    @Test
    void 더_오래된_체결은_발행하지_않는다() {
        listener.onTrade(new TradeEvent(1L, 10_300L, 7L, T0.plusSeconds(5)));

        listener.onTrade(new TradeEvent(1L, 9_000L, 1L, T0));

        assertThat(store.findPrice(1L)).contains(10_300L);
        verify(publisher, never()).publishPrice(any(), org.mockito.ArgumentMatchers.eq(
                new PriceResponse(1L, 9_000L, 1L, T0.toEpochMilli())));
    }
}
