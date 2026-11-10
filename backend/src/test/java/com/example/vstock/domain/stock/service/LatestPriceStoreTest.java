package com.example.vstock.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LatestPriceStoreTest {

    private static final Instant T0 = Instant.parse("2026-10-27T00:00:00Z");

    private final LatestPriceStore store = new LatestPriceStore();

    @Test
    void 체결이_없으면_현재가가_비어_있다() {
        assertThat(store.findPrice(1L)).isEmpty();
    }

    @Test
    void 체결이_들어오면_현재가가_갱신된다() {
        assertThat(store.update(1L, 10_000L, T0)).isTrue();
        assertThat(store.update(1L, 10_200L, T0.plusSeconds(1))).isTrue();

        assertThat(store.findPrice(1L)).contains(10_200L);
    }

    @Test
    void 더_오래된_체결은_현재가를_되돌리지_않는다() {
        store.update(1L, 10_200L, T0.plusSeconds(5));

        boolean applied = store.update(1L, 9_000L, T0);

        assertThat(applied).isFalse();
        assertThat(store.findPrice(1L)).contains(10_200L);
    }

    @Test
    void 같은_시각의_체결은_나중에_도착한_값으로_갱신된다() {
        store.update(1L, 10_000L, T0);

        assertThat(store.update(1L, 10_100L, T0)).isTrue();
        assertThat(store.findPrice(1L)).contains(10_100L);
    }

    @Test
    void 종목마다_현재가가_따로_관리된다() {
        store.update(1L, 10_000L, T0);
        store.update(2L, 55_000L, T0);

        assertThat(store.findPrice(1L)).contains(10_000L);
        assertThat(store.findPrice(2L)).contains(55_000L);
    }
}
