package com.example.vstock.domain.stock.event;

import java.time.Instant;

/**
 * 체결 1건. 체결 엔진 연동부가 발행하고 {@code CandleAggregator}가 구독한다.
 * 봉의 경계는 {@code executedAt}으로 계산한다. 엔진이 체결마다 주는 {@code executedAt}(UTC epoch 밀리초)을
 * 연동부가 {@code Instant.ofEpochMilli}로 옮겨 담는다. 한 주문의 체결 여러 건은 시각이 같을 수 있으므로,
 * 연동부는 응답의 {@code trades[]}를 배열 순서대로 발행해야 한다(같은 시각이면 나중에 발행한 체결이 종가가 된다).
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
