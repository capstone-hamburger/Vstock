package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.global.websocket.SubscriptionRegistry;
import com.example.vstock.global.websocket.WebSocketDestination;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목 관련 변경분을 구독자에게 발행한다.
 *
 * 기능 코드가 {@link SimpMessagingTemplate}과 경로 문자열을 직접 다루지 않게 가린다.
 * 구독자가 없으면 메시지는 예외 없이 버려진다 — 놓친 구간은 REST 스냅샷으로 다시 받는 것을 전제한다.
 */
@Component
@RequiredArgsConstructor
public class StockMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final SubscriptionRegistry subscriptionRegistry;

    // 아직 보내지 않은 진행 중인 봉의 최신 상태(경로 → 봉). candleLock 안에서만 읽고 쓴다.
    private final Map<String, CandleResponse> pendingCandles = new HashMap<>();
    private final Object candleLock = new Object();

    /** 최신 체결가를 발행한다. payload 타입은 차트 기능에서 DTO로 구체화한다. */
    public void publishPrice(Long stockId, Object payload) {
        messagingTemplate.convertAndSend(WebSocketDestination.stockPrice(stockId), payload);
    }

    /** 호가 변동을 발행한다. payload 타입은 호가창 기능에서 DTO로 구체화한다. */
    public void publishOrderbook(Long stockId, Object payload) {
        messagingTemplate.convertAndSend(WebSocketDestination.stockOrderbook(stockId), payload);
    }

    /**
     * 봉의 전체 상태를 발행한다. 클라이언트는 같은 time의 봉을 교체한다.
     *
     * <ul>
     *   <li>그 간격을 구독하는 연결이 없으면 보내지 않는다. 화면을 열면 REST로 현재 봉부터 받는다.</li>
     *   <li>진행 중인 봉은 바로 보내지 않고 경로별로 최신 상태만 모아 두었다가
     *       {@link #flushPendingCandles()}가 일정 간격으로 보낸다. 봉은 통째로 교체하는 값이라
     *       중간 상태를 건너뛰어도 결과가 같다.</li>
     *   <li>마감된 봉은 즉시 보낸다. 모아 둔 이전 진행 상태는 마감 값에 덮이므로 버린다.</li>
     * </ul>
     */
    public void publishCandle(Long stockId, CandleInterval interval, CandleResponse candle) {
        String destination = WebSocketDestination.stockCandle(stockId, interval.getCode());
        synchronized (candleLock) {
            if (!subscriptionRegistry.hasSubscribers(destination)) {
                pendingCandles.remove(destination);
                return;
            }
            if (candle.finalized()) {
                // 마감 메시지를 보내는 동안 flush가 더 오래된 진행 상태를 뒤따라 보내지 못하게 같은 락 안에서 보낸다.
                pendingCandles.remove(destination);
                messagingTemplate.convertAndSend(destination, candle);
            } else {
                pendingCandles.put(destination, candle);
            }
        }
    }

    /** 모아 둔 진행 중인 봉을 경로별 최신 상태로 한 번씩 보낸다. */
    @Scheduled(fixedDelayString = "${vstock.websocket.candle-flush-millis:100}")
    public void flushPendingCandles() {
        synchronized (candleLock) {
            pendingCandles.forEach((destination, candle) -> {
                if (subscriptionRegistry.hasSubscribers(destination)) {
                    messagingTemplate.convertAndSend(destination, candle);
                }
            });
            pendingCandles.clear();
        }
    }
}
