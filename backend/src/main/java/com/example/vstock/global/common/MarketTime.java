package com.example.vstock.global.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 시세 시간의 기준. 봉의 시작 시각은 모두 이 시간대의 {@link LocalDateTime}으로 저장·계산한다.
 * 클라이언트에는 시간대 혼선이 없도록 epoch 초로 내려보낸다.
 */
public final class MarketTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private MarketTime() {
    }

    public static long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZONE).toEpochSecond();
    }
}
