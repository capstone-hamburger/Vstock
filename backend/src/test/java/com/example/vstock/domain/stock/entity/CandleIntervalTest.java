package com.example.vstock.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CandleIntervalTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 27, 9, 13, 42);

    @Test
    void 코드로_interval을_찾는다() {
        assertThat(CandleInterval.fromCode("1m")).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(CandleInterval.fromCode("5m")).isEqualTo(CandleInterval.FIVE_MINUTES);
        assertThat(CandleInterval.fromCode("1h")).isEqualTo(CandleInterval.ONE_HOUR);
        assertThat(CandleInterval.fromCode("1d")).isEqualTo(CandleInterval.ONE_DAY);
    }

    @Test
    void 지원하지_않는_코드는_예외가_발생한다() {
        assertThatThrownBy(() -> CandleInterval.fromCode("1y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleInterval.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시각이_속한_봉의_시작_시각을_구한다() {
        assertThat(CandleInterval.ONE_MINUTE.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 13, 0));
        assertThat(CandleInterval.FIVE_MINUTES.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 10, 0));
        assertThat(CandleInterval.ONE_HOUR.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 0, 0));
        assertThat(CandleInterval.ONE_DAY.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 0, 0, 0));
    }

    @Test
    void 구간의_경계_시각은_그_구간의_첫_봉에_속한다() {
        LocalDateTime boundary = LocalDateTime.of(2026, 10, 27, 9, 5, 0);

        assertThat(CandleInterval.FIVE_MINUTES.bucketStart(boundary)).isEqualTo(boundary);
        assertThat(CandleInterval.FIVE_MINUTES.bucketStart(boundary.minusNanos(1)))
                .isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 0, 0));
    }
}
