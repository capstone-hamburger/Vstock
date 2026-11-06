package com.example.vstock.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

class SubscriptionRegistryTest {

    private static final String DESTINATION = "/topic/stocks/1/candles/1m";

    private SubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SubscriptionRegistry();
    }

    @Test
    void 구독하면_그_경로에_구독자가_있다() {
        subscribe("s1", "sub-0", DESTINATION);

        assertThat(registry.hasSubscribers(DESTINATION)).isTrue();
        assertThat(registry.hasSubscribers("/topic/stocks/1/candles/10m")).isFalse();
    }

    @Test
    void 아무도_구독하지_않았으면_구독자가_없다() {
        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
    }

    @Test
    void 구독을_해제하면_구독자가_없어진다() {
        subscribe("s1", "sub-0", DESTINATION);

        unsubscribe("s1", "sub-0");

        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
    }

    @Test
    void 구독자가_둘이면_하나가_해제돼도_남아_있다() {
        subscribe("s1", "sub-0", DESTINATION);
        subscribe("s2", "sub-0", DESTINATION);

        unsubscribe("s1", "sub-0");

        assertThat(registry.hasSubscribers(DESTINATION)).isTrue();
        unsubscribe("s2", "sub-0");
        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
    }

    @Test
    void 연결이_끊기면_그_세션의_구독이_모두_정리된다() {
        subscribe("s1", "sub-0", DESTINATION);
        subscribe("s1", "sub-1", "/topic/stocks/1/price");

        disconnect("s1");

        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
        assertThat(registry.hasSubscribers("/topic/stocks/1/price")).isFalse();
    }

    @Test
    void 연결이_끊겨도_다른_세션의_구독은_남는다() {
        subscribe("s1", "sub-0", DESTINATION);
        subscribe("s2", "sub-0", DESTINATION);

        disconnect("s1");

        assertThat(registry.hasSubscribers(DESTINATION)).isTrue();
    }

    @Test
    void 같은_구독_ID로_다시_구독해도_한_번만_센다() {
        subscribe("s1", "sub-0", DESTINATION);
        subscribe("s1", "sub-0", DESTINATION);

        unsubscribe("s1", "sub-0");

        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
    }

    @Test
    void 알_수_없는_구독이나_세션의_해제와_종료는_무시한다() {
        unsubscribe("unknown", "sub-0");
        disconnect("unknown");

        assertThat(registry.hasSubscribers(DESTINATION)).isFalse();
    }

    private void subscribe(String sessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        registry.handleSubscribe(new SessionSubscribeEvent(this, messageOf(accessor)));
    }

    private void unsubscribe(String sessionId, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        registry.handleUnsubscribe(new SessionUnsubscribeEvent(this, messageOf(accessor)));
    }

    private void disconnect(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        registry.handleDisconnect(
                new SessionDisconnectEvent(this, messageOf(accessor), sessionId, CloseStatus.NORMAL));
    }

    private Message<byte[]> messageOf(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
