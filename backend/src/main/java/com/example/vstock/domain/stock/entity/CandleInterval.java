package com.example.vstock.domain.stock.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * 봉 간격. DB에는 1분봉만 저장하고, 나머지는 1분봉을 모아서 만든다.
 */
public enum CandleInterval {
    ONE_MINUTE("1m", 60),
    FIVE_MINUTES("5m", 5 * 60),
    ONE_HOUR("1h", 60 * 60),
    ONE_DAY("1d", 24 * 60 * 60);

    private final String code;
    private final int seconds;

    CandleInterval(String code, int seconds) {
        this.code = code;
        this.seconds = seconds;
    }

    public String getCode() {
        return code;
    }

    public int getSeconds() {
        return seconds;
    }

    public static CandleInterval fromCode(String code) {
        return Arrays.stream(values())
                .filter(interval -> interval.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 interval입니다. interval=" + code));
    }

    /** 시각이 속한 봉의 시작 시각. 시간대 보정 없이 현지 시각 그대로 자정을 기준으로 자른다. */
    public LocalDateTime bucketStart(LocalDateTime time) {
        long epoch = time.toEpochSecond(ZoneOffset.UTC);
        long bucket = Math.floorDiv(epoch, seconds) * seconds;
        return LocalDateTime.ofEpochSecond(bucket, 0, ZoneOffset.UTC);
    }
}
