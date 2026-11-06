package com.example.vstock.global.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * 경로별 구독자 수를 센다. 구독자가 없는 경로로는 메시지를 만들어 보낼 필요가 없어서,
 * 발행하는 쪽이 {@link #hasSubscribers(String)}로 먼저 확인한다.
 *
 * <p>UNSUBSCRIBE 프레임에는 경로가 없고 구독 ID만 있어서, 세션별로 (구독 ID → 경로)를 기억해 둔다.
 * 연결이 끊기면 그 세션의 구독을 한꺼번에 정리한다.
 */
@Component
public class SubscriptionRegistry {

    // (세션 ID → (구독 ID → 경로)). 이 객체의 락 안에서만 바꾼다.
    private final Map<String, Map<String, String>> destinationsBySession = new HashMap<>();
    // 경로 → 구독자 수. 읽기는 락 없이 한다.
    private final Map<String, Integer> subscriberCounts = new ConcurrentHashMap<>();

    @EventListener
    public synchronized void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String destination = accessor.getDestination();
        if (sessionId == null || subscriptionId == null || destination == null) {
            return;
        }

        String previous = destinationsBySession
                .computeIfAbsent(sessionId, key -> new HashMap<>())
                .put(subscriptionId, destination);
        if (previous != null) {
            decrement(previous);
        }
        subscriberCounts.merge(destination, 1, Integer::sum);
    }

    @EventListener
    public synchronized void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) {
            return;
        }

        Map<String, String> subscriptions = destinationsBySession.get(sessionId);
        if (subscriptions == null) {
            return;
        }
        String destination = subscriptions.remove(subscriptionId);
        if (destination != null) {
            decrement(destination);
        }
        if (subscriptions.isEmpty()) {
            destinationsBySession.remove(sessionId);
        }
    }

    @EventListener
    public synchronized void handleDisconnect(SessionDisconnectEvent event) {
        Map<String, String> subscriptions = destinationsBySession.remove(event.getSessionId());
        if (subscriptions == null) {
            return;
        }
        subscriptions.values().forEach(this::decrement);
    }

    /** 이 경로를 구독 중인 연결이 하나라도 있는지. */
    public boolean hasSubscribers(String destination) {
        return subscriberCounts.containsKey(destination);
    }

    private void decrement(String destination) {
        subscriberCounts.computeIfPresent(destination, (key, count) -> count <= 1 ? null : count - 1);
    }
}
