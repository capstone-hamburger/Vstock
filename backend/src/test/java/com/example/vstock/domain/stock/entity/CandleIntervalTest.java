package com.example.vstock.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CandleIntervalTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 27, 9, 13, 42, 123_000_000);

    @Test
    void 코드로_interval을_찾는다() {
        assertThat(CandleInterval.fromCode("1m")).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(CandleInterval.fromCode("10m")).isEqualTo(CandleInterval.TEN_MINUTES);
        assertThat(CandleInterval.fromCode("1h")).isEqualTo(CandleInterval.ONE_HOUR);
        assertThat(CandleInterval.fromCode("1d")).isEqualTo(CandleInterval.ONE_DAY);
        assertThat(CandleInterval.fromCode("1M")).isEqualTo(CandleInterval.ONE_MONTH);
    }

    @Test
    void 분과_달은_대소문자로_구분한다() {
        assertThat(CandleInterval.fromCode("1m")).isNotEqualTo(CandleInterval.fromCode("1M"));
    }

    @Test
    void 지원하지_않는_코드는_예외가_발생한다() {
        assertThatThrownBy(() -> CandleInterval.fromCode("5m"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleInterval.fromCode("1y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleInterval.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시각이_속한_봉의_시작_시각을_구한다() {
        assertThat(CandleInterval.ONE_MINUTE.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 13));
        assertThat(CandleInterval.TEN_MINUTES.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 10));
        assertThat(CandleInterval.ONE_HOUR.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 0));
        assertThat(CandleInterval.ONE_DAY.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 27, 0, 0));
        assertThat(CandleInterval.ONE_MONTH.bucketStart(TIME)).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    @Test
    void 구간의_경계_시각은_그_구간의_첫_봉에_속한다() {
        LocalDateTime boundary = LocalDateTime.of(2026, 10, 27, 9, 10);

        assertThat(CandleInterval.TEN_MINUTES.bucketStart(boundary)).isEqualTo(boundary);
        assertThat(CandleInterval.TEN_MINUTES.bucketStart(boundary.minusNanos(1)))
                .isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 0));
    }

    @Test
    void 달의_첫날과_마지막_날은_같은_달봉에_속한다() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 0, 0);

        assertThat(CandleInterval.ONE_MONTH.bucketStart(start)).isEqualTo(start);
        assertThat(CandleInterval.ONE_MONTH.bucketStart(LocalDateTime.of(2026, 12, 31, 23, 59, 59)))
                .isEqualTo(start);
        assertThat(CandleInterval.ONE_MONTH.bucketStart(LocalDateTime.of(2027, 1, 1, 0, 0)))
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void 한_단계_낮은_간격을_알려준다() {
        assertThat(CandleInterval.ONE_MINUTE.getLower()).isNull();
        assertThat(CandleInterval.TEN_MINUTES.getLower()).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(CandleInterval.ONE_HOUR.getLower()).isEqualTo(CandleInterval.TEN_MINUTES);
        assertThat(CandleInterval.ONE_DAY.getLower()).isEqualTo(CandleInterval.ONE_HOUR);
        assertThat(CandleInterval.ONE_MONTH.getLower()).isEqualTo(CandleInterval.ONE_DAY);
    }
}
