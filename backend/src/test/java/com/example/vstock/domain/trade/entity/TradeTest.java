package com.example.vstock.domain.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TradeTest {

    private static final Instant EXECUTED_AT = Instant.parse("2026-10-27T00:03:21.123456789Z");

    private final Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);

    @Test
    void 체결_시각을_한국_시간의_밀리초까지로_바꿔_저장한다() {
        Trade trade = Trade.of(stock, 70_000L, 3L, EXECUTED_AT, 1L, 2L, "session-1", 7L);

        assertThat(trade.getExecutedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 3, 21, 123_000_000));
    }

    @Test
    void 필드를_그대로_담는다() {
        Trade trade = Trade.of(stock, 70_000L, 3L, EXECUTED_AT, 1L, 2L, "session-1", 7L);

        assertThat(trade.getStock()).isSameAs(stock);
        assertThat(trade.getPrice()).isEqualTo(70_000L);
        assertThat(trade.getQuantity()).isEqualTo(3L);
        assertThat(trade.getBuyOrderId()).isEqualTo(1L);
        assertThat(trade.getSellOrderId()).isEqualTo(2L);
        assertThat(trade.getEngineSessionId()).isEqualTo("session-1");
        assertThat(trade.getEngineTradeId()).isEqualTo(7L);
    }

    @Test
    void 엔진_세션_ID는_없어도_된다() {
        Trade trade = Trade.of(stock, 70_000L, 3L, EXECUTED_AT, 1L, 2L, null, 7L);

        assertThat(trade.getEngineSessionId()).isNull();
    }

    @Test
    void 가격이_0_이하면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(stock, 0L, 3L, EXECUTED_AT, 1L, 2L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수량이_0_이하면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(stock, 70_000L, 0L, EXECUTED_AT, 1L, 2L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 종목이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(null, 70_000L, 3L, EXECUTED_AT, 1L, 2L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 체결_시각이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(stock, 70_000L, 3L, null, 1L, 2L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문_ID가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(stock, 70_000L, 3L, EXECUTED_AT, null, 2L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Trade.of(stock, 70_000L, 3L, EXECUTED_AT, 1L, null, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 엔진_체결_ID가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Trade.of(stock, 70_000L, 3L, EXECUTED_AT, 1L, 2L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
