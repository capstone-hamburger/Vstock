package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.global.websocket.WebSocketDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    /** 최신 체결가를 발행한다. payload 타입은 차트 기능에서 DTO로 구체화한다. */
    public void publishPrice(Long stockId, Object payload) {
        messagingTemplate.convertAndSend(WebSocketDestination.stockPrice(stockId), payload);
    }

    /** 호가 변동을 발행한다. payload 타입은 호가창 기능에서 DTO로 구체화한다. */
    public void publishOrderbook(Long stockId, Object payload) {
        messagingTemplate.convertAndSend(WebSocketDestination.stockOrderbook(stockId), payload);
    }

    /** 진행 중이거나 마감된 봉의 전체 상태를 발행한다. 클라이언트는 같은 time의 봉을 교체한다. */
    public void publishCandle(Long stockId, CandleInterval interval, CandleResponse candle) {
        messagingTemplate.convertAndSend(WebSocketDestination.stockCandle(stockId, interval.getCode()), candle);
    }
}
