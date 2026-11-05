package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.global.common.MarketTime;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 마감되지 않았거나 늦은 체결로 고치는 중인 봉. {@link CandleAggregator}의 락 안에서만 읽고 쓴다.
 *
 * <p>open과 close는 도착 순서가 아니라 <b>체결 시각</b> 기준이다. 순서가 뒤바뀌어 도착한 체결도 봉에 합칠 수
 * 있도록 open·close가 된 체결의 시각을 함께 들고 있다. 시각이 같으면 먼저 도착한 체결이 open, 나중에 도착한
 * 체결이 close다.
 */
final class LiveCandle {

    private final LocalDateTime startTime;
    private long open;
    private long high;
    private long low;
    private long close;
    private long volume;
    private long firstTradeAt;
    private long lastTradeAt;
    private long seq;

    LiveCandle(LocalDateTime startTime, long firstPrice, long tradedAt) {
        this.startTime = startTime;
        this.open = firstPrice;
        this.high = firstPrice;
        this.low = firstPrice;
        this.close = firstPrice;
        this.firstTradeAt = tradedAt;
        this.lastTradeAt = tradedAt;
    }

    /** 저장된 봉 하나를 같은 값의 LiveCandle로 옮긴다. 상위 봉 복구와 늦은 체결 보정의 재료로 쓴다. */
    static LiveCandle fromStored(StockCandle stored) {
        LiveCandle live = new LiveCandle(stored.getTimestamp(), stored.getOpenPrice(), stored.getFirstTradeAt());
        live.high = stored.getHighPrice();
        live.low = stored.getLowPrice();
        live.close = stored.getClosePrice();
        live.volume = stored.getVolume();
        live.lastTradeAt = stored.getLastTradeAt();
        return live;
    }

    /**
     * 낮은 간격의 봉들을 한 개의 상위 봉으로 합친다. parts는 비어 있으면 안 된다.
     * open은 체결 시각이 가장 이른 봉, close는 가장 늦은 봉의 값을 쓴다.
     */
    static LiveCandle combine(LocalDateTime startTime, List<LiveCandle> parts) {
        LiveCandle first = parts.get(0);
        LiveCandle combined = new LiveCandle(startTime, first.open, first.firstTradeAt);
        combined.close = first.close;
        combined.lastTradeAt = first.lastTradeAt;
        combined.high = first.high;
        combined.low = first.low;
        combined.volume = first.volume;
        for (LiveCandle part : parts.subList(1, parts.size())) {
            combined.high = Math.max(combined.high, part.high);
            combined.low = Math.min(combined.low, part.low);
            combined.volume += part.volume;
            if (part.firstTradeAt < combined.firstTradeAt) {
                combined.open = part.open;
                combined.firstTradeAt = part.firstTradeAt;
            }
            if (part.lastTradeAt >= combined.lastTradeAt) {
                combined.close = part.close;
                combined.lastTradeAt = part.lastTradeAt;
            }
        }
        return combined;
    }

    /** 체결 하나를 합친다. 늦게 도착한 체결이면 open·close를 시각 기준으로 다시 정한다. */
    void apply(long price, long quantity, long seq, long tradedAt) {
        high = Math.max(high, price);
        low = Math.min(low, price);
        volume += quantity;
        if (tradedAt < firstTradeAt) {
            open = price;
            firstTradeAt = tradedAt;
        }
        if (tradedAt >= lastTradeAt) {
            close = price;
            lastTradeAt = tradedAt;
        }
        this.seq = seq;
    }

    LocalDateTime startTime() {
        return startTime;
    }

    long open() {
        return open;
    }

    long high() {
        return high;
    }

    long low() {
        return low;
    }

    long close() {
        return close;
    }

    long volume() {
        return volume;
    }

    long firstTradeAt() {
        return firstTradeAt;
    }

    long lastTradeAt() {
        return lastTradeAt;
    }

    CandleResponse toResponse(boolean finalized) {
        return new CandleResponse(MarketTime.toEpochSecond(startTime), open, high, low, close, volume, finalized, seq);
    }
}
