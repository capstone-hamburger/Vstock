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

    /** 저장된 1분봉들(시간순)로 상위 봉의 진행 상태를 복원한다. */
    static LiveCandle restoreFrom(LocalDateTime startTime, List<StockCandle> minuteCandles) {
        LiveCandle live = new LiveCandle(startTime, minuteCandles.get(0).getOpenPrice());
        for (StockCandle minute : minuteCandles) {
            live.high = Math.max(live.high, minute.getHighPrice());
            live.low = Math.min(live.low, minute.getLowPrice());
            live.close = minute.getClosePrice();
            live.volume += minute.getVolume();
        }
        return live;
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
