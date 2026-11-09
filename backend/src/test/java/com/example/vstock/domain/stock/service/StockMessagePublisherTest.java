package com.example.vstock.domain.stock.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.global.websocket.SubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StockMessagePublisherTest {

    private static final Long STOCK_ID = 1L;
    private static final String ONE_MINUTE = "/topic/stocks/1/candles/1m";
    private static final String TEN_MINUTES = "/topic/stocks/1/candles/10m";

    private SimpMessagingTemplate messagingTemplate;
    private SubscriptionRegistry registry;
    private StockMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        registry = mock(SubscriptionRegistry.class);
        publisher = new StockMessagePublisher(messagingTemplate, registry);
    }

    @Test
    void 구독자가_없는_간격의_봉은_보내지_않는다() {
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, true, 2));
        publisher.flushPendingCandles();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void 진행_중인_봉은_바로_보내지_않고_flush할_때_보낸다() {
        subscribed(ONE_MINUTE);
        CandleResponse candle = candle(100, false, 1);

        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle);
        verify(messagingTemplate, never()).convertAndSend(eq(ONE_MINUTE), any(Object.class));

        publisher.flushPendingCandles();
        verify(messagingTemplate).convertAndSend(ONE_MINUTE, candle);
    }

    @Test
    void 같은_간격의_진행_봉은_flush_사이의_마지막_상태만_보낸다() {
        subscribed(ONE_MINUTE);
        CandleResponse last = candle(130, false, 3);

        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(120, false, 2));
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, last);
        publisher.flushPendingCandles();

        verify(messagingTemplate, times(1)).convertAndSend(eq(ONE_MINUTE), any(Object.class));
        verify(messagingTemplate).convertAndSend(ONE_MINUTE, last);
    }

    @Test
    void flush한_뒤에는_같은_봉을_다시_보내지_않는다() {
        subscribed(ONE_MINUTE);
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));

        publisher.flushPendingCandles();
        publisher.flushPendingCandles();

        verify(messagingTemplate, times(1)).convertAndSend(eq(ONE_MINUTE), any(Object.class));
    }

    @Test
    void 간격이_다르면_따로_모아서_각각_보낸다() {
        subscribed(ONE_MINUTE);
        subscribed(TEN_MINUTES);
        CandleResponse oneMinute = candle(100, false, 1);
        CandleResponse tenMinutes = candle(100, false, 1);

        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, oneMinute);
        publisher.publishCandle(STOCK_ID, CandleInterval.TEN_MINUTES, tenMinutes);
        publisher.flushPendingCandles();

        verify(messagingTemplate).convertAndSend(ONE_MINUTE, oneMinute);
        verify(messagingTemplate).convertAndSend(TEN_MINUTES, tenMinutes);
    }

    @Test
    void 구독하는_간격만_보낸다() {
        subscribed(ONE_MINUTE);

        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));
        publisher.publishCandle(STOCK_ID, CandleInterval.TEN_MINUTES, candle(100, false, 1));
        publisher.flushPendingCandles();

        verify(messagingTemplate).convertAndSend(eq(ONE_MINUTE), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq(TEN_MINUTES), any(Object.class));
    }

    @Test
    void 마감된_봉은_바로_보내고_모아_둔_진행_상태는_버린다() {
        subscribed(ONE_MINUTE);
        CandleResponse closed = candle(150, true, 5);
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 4));

        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, closed);
        publisher.flushPendingCandles();

        InOrder inOrder = Mockito.inOrder(messagingTemplate);
        inOrder.verify(messagingTemplate).convertAndSend(ONE_MINUTE, closed);
        verify(messagingTemplate, times(1)).convertAndSend(eq(ONE_MINUTE), any(Object.class));
    }

    @Test
    void flush하기_전에_구독자가_사라지면_보내지_않는다() {
        when(registry.hasSubscribers(ONE_MINUTE)).thenReturn(true, false);
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));

        publisher.flushPendingCandles();

        verify(messagingTemplate, never()).convertAndSend(eq(ONE_MINUTE), any(Object.class));
    }

    @Test
    void 구독자가_사라진_뒤_다시_구독해도_옛_진행_상태를_보내지_않는다() {
        when(registry.hasSubscribers(ONE_MINUTE)).thenReturn(true, false, true);
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(100, false, 1));
        // 구독자가 없을 때의 발행이 모아 둔 옛 상태를 지운다.
        publisher.publishCandle(STOCK_ID, CandleInterval.ONE_MINUTE, candle(110, false, 2));

        publisher.flushPendingCandles();

        verify(messagingTemplate, never()).convertAndSend(eq(ONE_MINUTE), any(Object.class));
    }

    @Test
    void 체결가와_호가는_구독자를_확인하지_않고_그대로_보낸다() {
        publisher.publishPrice(STOCK_ID, "price");
        publisher.publishOrderbook(STOCK_ID, "orderbook");

        verify(messagingTemplate).convertAndSend("/topic/stocks/1/price", (Object) "price");
        verify(messagingTemplate).convertAndSend("/topic/stocks/1/orderbook", (Object) "orderbook");
        verifyNoInteractions(registry);
    }

    private void subscribed(String destination) {
        when(registry.hasSubscribers(destination)).thenReturn(true);
    }

    private CandleResponse candle(long close, boolean finalized, long seq) {
        return new CandleResponse(1_793_059_200L, 100, 150, 90, close, 10, finalized, seq);
    }
}
