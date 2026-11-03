package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.global.common.MarketTime;
import java.time.LocalDateTime;
import java.util.List;

/** 아직 마감되지 않은 봉. {@link CandleAggregator}의 락 안에서만 읽고 쓴다. */
final class LiveCandle {

    private final LocalDateTime startTime;
    private long open;
    private long high;
    private long low;
    private long close;
    private long volume;
    private long seq;

    LiveCandle(LocalDateTime startTime, long firstPrice) {
        this.startTime = startTime;
        this.open = firstPrice;
        this.high = firstPrice;
        this.low = firstPrice;
        this.close = firstPrice;
    }

    /** 저장된 봉 하나를 같은 값의 LiveCandle로 옮긴다. 상위 봉을 복구할 때 재료로 쓴다. */
    static LiveCandle fromStored(StockCandle stored) {
        LiveCandle live = new LiveCandle(stored.getTimestamp(), stored.getOpenPrice());
        live.high = stored.getHighPrice();
        live.low = stored.getLowPrice();
        live.close = stored.getClosePrice();
        live.volume = stored.getVolume();
        return live;
    }

    /** 시간순으로 놓인 낮은 간격의 봉들을 한 개의 상위 봉으로 합친다. parts는 비어 있으면 안 된다. */
    static LiveCandle combine(LocalDateTime startTime, List<LiveCandle> parts) {
        LiveCandle combined = new LiveCandle(startTime, parts.get(0).open);
        for (LiveCandle part : parts) {
            combined.high = Math.max(combined.high, part.high);
            combined.low = Math.min(combined.low, part.low);
            combined.close = part.close;
            combined.volume += part.volume;
        }
        return combined;
    }

    void apply(long price, long quantity, long seq) {
        high = Math.max(high, price);
        low = Math.min(low, price);
        close = price;
        volume += quantity;
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

    CandleResponse toResponse(boolean finalized) {
        return new CandleResponse(MarketTime.toEpochSecond(startTime), open, high, low, close, volume, finalized, seq);
    }
}
