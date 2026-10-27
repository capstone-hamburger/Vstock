package com.example.vstock.domain.stock.event;

import java.time.Instant;

/**
 * 체결 1건. 체결 엔진 연동부가 발행하고 {@code CandleAggregator}가 구독한다.
 * 봉의 경계는 수신 시각이 아니라 엔진이 알려준 {@code executedAt}으로 계산한다.
 */
public record TradeEvent(Long stockId, long price, long quantity, Instant executedAt) {

    public TradeEvent {
        if (stockId == null) {
            throw new IllegalArgumentException("stockId은(는) null일 수 없습니다.");
        }
        if (executedAt == null) {
            throw new IllegalArgumentException("executedAt은(는) null일 수 없습니다.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price는 0보다 커야 합니다. price=" + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다. quantity=" + quantity);
        }
    }
}
