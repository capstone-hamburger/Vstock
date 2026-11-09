package com.example.vstock.domain.stock.entity;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 봉 간격. 선언 순서는 낮은 단위부터이며, 마감되는 모든 간격의 봉을 DB에 저장한다.
 *
 * <p>{@code 1y} 같은 조회 기간은 간격이 아니다. 1년치를 보려면 {@link #ONE_DAY}나
 * {@link #ONE_MONTH}로 조회한다.
 */
public enum CandleInterval {
    ONE_MINUTE("1m", null),
    TEN_MINUTES("10m", ONE_MINUTE),
    ONE_HOUR("1h", TEN_MINUTES),
    ONE_DAY("1d", ONE_HOUR),
    ONE_MONTH("1M", ONE_DAY);

    private final String code;
    private final CandleInterval lower;

    CandleInterval(String code, CandleInterval lower) {
        this.code = code;
        this.lower = lower;
    }

    /** 클라이언트가 쓰는 코드. 분(1m)과 달(1M)을 대소문자로 구분한다. */
    public String getCode() {
        return code;
    }

    /**
     * 이 간격의 봉을 채울 때 쓰는 한 단계 낮은 간격. 가장 낮은 간격이면 null.
     * 서버가 재시작됐을 때 진행 중인 봉을 저장된 낮은 간격의 봉으로 다시 만드는 데 쓴다.
     */
    public CandleInterval getLower() {
        return lower;
    }

    public static CandleInterval fromCode(String code) {
        return Arrays.stream(values())
                .filter(interval -> interval.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 interval입니다. interval=" + code));
    }

    /** 시각이 속한 봉의 시작 시각. 시간대 보정 없이 현지 시각 그대로 자른다. */
    public LocalDateTime bucketStart(LocalDateTime time) {
        return switch (this) {
            case ONE_MINUTE -> time.withSecond(0).withNano(0);
            case TEN_MINUTES -> time.withMinute(time.getMinute() / 10 * 10).withSecond(0).withNano(0);
            case ONE_HOUR -> time.withMinute(0).withSecond(0).withNano(0);
            case ONE_DAY -> time.toLocalDate().atStartOfDay();
            case ONE_MONTH -> time.toLocalDate().withDayOfMonth(1).atStartOfDay();
        };
    }
}
