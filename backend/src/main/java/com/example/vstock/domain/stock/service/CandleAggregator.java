package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.event.TradeEvent;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 체결을 받아 종목별·interval별 진행 중인 봉을 메모리에서 갱신하고 소켓으로 푸시한다.
 * 봉이 마감될 때 그 간격의 봉을 DB에 저장한다. 1분봉부터 1달봉까지 모두 저장하므로 조회는 계산 없이
 * 요청한 간격의 행만 읽는다.
 *
 * 모든 상태 접근은 이 객체의 락 안에서 일어난다. 종목 수가 적고 갱신이 가벼워 락 하나로 충분하다.
 * 서버가 재시작되면 진행 중이던 1분봉은 유실되고, 나머지 진행 중인 봉은 한 단계 낮은 간격의
 * 저장된 봉으로 다시 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleAggregator {

    private final StockCandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final StockMessagePublisher messagePublisher;
    private final Clock clock;

    private final Map<CandleKey, LiveCandle> current = new HashMap<>();
    private final Map<Long, Long> lastSeq = new HashMap<>();

    /** 체결 트랜잭션이 있으면 커밋된 뒤에, 없으면 바로 호출된다. 롤백된 체결은 차트에 반영되지 않는다. */
    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void onTrade(TradeEvent event) {
        LocalDateTime tradedAt = LocalDateTime.ofInstant(event.executedAt(), MarketTime.ZONE);
        long seq = lastSeq.merge(event.stockId(), 1L, Long::sum);

        for (CandleInterval interval : CandleInterval.values()) {
            applyTrade(event, interval, interval.bucketStart(tradedAt), seq);
        }
    }

    private void applyTrade(TradeEvent event, CandleInterval interval, LocalDateTime bucket, long seq) {
        CandleKey key = new CandleKey(event.stockId(), interval);
        LiveCandle live = current.get(key);

        if (live != null && bucket.isBefore(live.startTime())) {
            log.warn("이미 지난 구간의 체결이라 무시한다. stockId={}, interval={}, executedAt={}",
                    event.stockId(), interval.getCode(), event.executedAt());
            return;
        }
        if (live != null && bucket.isAfter(live.startTime())) {
            finish(key, live);
            live = null;
        }
        if (live == null) {
            live = new LiveCandle(bucket, event.price());
            current.put(key, live);
        }

        live.apply(event.price(), event.quantity(), seq);
        messagePublisher.publishCandle(event.stockId(), interval, live.toResponse(false));
    }

    /**
     * 거래가 없어 다음 체결이 오지 않은 봉을 마감한다. 매분 1초에 실행해 직전 분의 봉을 닫는다.
     */
    @Scheduled(cron = "1 * * * * *")
    public synchronized void closeExpiredCandles() {
        LocalDateTime now = LocalDateTime.now(clock);

        Iterator<Map.Entry<CandleKey, LiveCandle>> entries = current.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<CandleKey, LiveCandle> entry = entries.next();
            CandleInterval interval = entry.getKey().interval();
            if (interval.bucketStart(now).isAfter(entry.getValue().startTime())) {
                finish(entry.getKey(), entry.getValue());
                entries.remove();
            }
        }
    }

    /**
     * 서버 시작 시 진행 중인 봉을 복구한다. 낮은 간격부터 차례로, 한 단계 낮은 간격의 저장된 봉과
     * 방금 복구한 낮은 간격의 진행 중인 봉을 합쳐 채운다. 1분봉은 복구할 수 없다.
     * 예: 09:44에 시작하면 10분봉은 1분봉 09:40~09:43으로, 1시간봉은 저장된 10분봉 09:00~09:30과
     * 방금 복구한 10분봉 09:40으로 채운다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void restore() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            for (Stock stock : stockRepository.findAll()) {
                for (CandleInterval interval : CandleInterval.values()) {
                    if (interval.getLower() != null) {
                        restoreFromLower(stock.getStockId(), interval, now);
                    }
                }
            }
        } catch (RuntimeException e) {
            // 복구 실패로 서버 기동까지 막지 않는다. 다음 체결부터 새로 쌓인다.
            log.error("진행 중인 봉 복구에 실패했다.", e);
        }
    }

    private void restoreFromLower(Long stockId, CandleInterval interval, LocalDateTime now) {
        CandleInterval lower = interval.getLower();
        LocalDateTime start = interval.bucketStart(now);

        List<LiveCandle> parts = new ArrayList<>();
        for (StockCandle stored : candleRepository.findCandlesAscending(stockId, lower, start, now)) {
            parts.add(LiveCandle.fromStored(stored));
        }
        LiveCandle lowerLive = current.get(new CandleKey(stockId, lower));
        if (lowerLive != null && !lowerLive.startTime().isBefore(start)) {
            parts.add(lowerLive);
        }

        if (!parts.isEmpty()) {
            current.put(new CandleKey(stockId, interval), LiveCandle.combine(start, parts));
        }
    }

    /** 진행 중인 봉의 스냅샷. 없으면 null. */
    public synchronized CandleResponse findCurrentCandle(Long stockId, CandleInterval interval) {
        LiveCandle live = current.get(new CandleKey(stockId, interval));
        return live == null ? null : live.toResponse(false);
    }

    private void finish(CandleKey key, LiveCandle live) {
        persist(key, live);
        messagePublisher.publishCandle(key.stockId(), key.interval(), live.toResponse(true));
    }

    // 저장이 끝난 뒤에 마감 메시지를 보낸다. 저장이 실패해도 체결 처리는 계속한다.
    private void persist(CandleKey key, LiveCandle live) {
        try {
            candleRepository.upsertCandle(key.stockId(), key.interval(), live.startTime(),
                    live.open(), live.high(), live.low(), live.close(), live.volume());
        } catch (RuntimeException e) {
            log.error("봉 저장에 실패했다. stockId={}, interval={}, startTime={}",
                    key.stockId(), key.interval().getCode(), live.startTime(), e);
        }
    }

    private record CandleKey(Long stockId, CandleInterval interval) {
    }
}
