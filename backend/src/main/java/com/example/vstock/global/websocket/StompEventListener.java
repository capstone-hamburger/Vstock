package com.example.vstock.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * 소켓 연결·구독 생명주기 로깅.
 *
 * 구독자가 없을 때 발행된 메시지는 조용히 버려지므로, 프론트가 "데이터가 안 온다"고 할 때
 * 연결·구독이 실제로 잡혔는지 확인할 수 있는 지점이 필요하다.
 */
@Slf4j
@Component
public class StompEventListener {

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        log.debug("소켓 연결됨. sessionId={}", sessionIdOf(event.getMessage()));
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("구독 시작. sessionId={}, destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("구독 해제. sessionId={}, subscriptionId={}",
                accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        log.debug("소켓 연결 종료. sessionId={}, status={}",
                event.getSessionId(), event.getCloseStatus());
    }

    private String sessionIdOf(Message<?> message) {
        return StompHeaderAccessor.wrap(message).getSessionId();
    }
}
