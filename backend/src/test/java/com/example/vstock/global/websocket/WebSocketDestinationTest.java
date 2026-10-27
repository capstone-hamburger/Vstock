package com.example.vstock.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebSocketDestinationTest {

    @Test
    void 종목_체결가_경로를_만든다() {
        assertThat(WebSocketDestination.stockPrice(7L)).isEqualTo("/topic/stocks/7/price");
    }

    @Test
    void 종목_호가_경로를_만든다() {
        assertThat(WebSocketDestination.stockOrderbook(7L)).isEqualTo("/topic/stocks/7/orderbook");
    }

    @Test
    void 종목이_다르면_경로가_겹치지_않는다() {
        assertThat(WebSocketDestination.stockPrice(1L))
                .isNotEqualTo(WebSocketDestination.stockPrice(2L));
    }

    @Test
    void 구독_경로는_브로커_prefix로_시작한다() {
        assertThat(WebSocketDestination.stockPrice(1L))
                .startsWith(WebSocketDestination.SUBSCRIBE_PREFIX);
        assertThat(WebSocketDestination.stockOrderbook(1L))
                .startsWith(WebSocketDestination.SUBSCRIBE_PREFIX);
    }

    @Test
    void stockId가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> WebSocketDestination.stockPrice(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebSocketDestination.stockOrderbook(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
