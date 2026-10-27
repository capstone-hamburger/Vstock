package com.example.vstock.global.config;

import com.example.vstock.global.websocket.WebSocketDestination;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 기본 설정.
 *
 * 클라이언트는 {@code /ws}로 연결한 뒤 {@link WebSocketDestination}의 경로를 구독한다.
 * 초기 데이터는 REST로 받고 소켓으로는 변경분만 내려보내는 구조를 전제한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String HANDSHAKE_ENDPOINT = "/ws";

    private final List<String> allowedOriginPatterns;

    public WebSocketConfig(
            @Value("${vstock.websocket.allowed-origins}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 핸드셰이크는 CORS 대상이 아니라 Origin 검사를 직접 걸어야 한다.
        registry.addEndpoint(HANDSHAKE_ENDPOINT)
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 인메모리 브로커. 서버를 여러 대로 늘리면 외부 브로커(Redis/RabbitMQ)로 교체해야 한다.
        registry.enableSimpleBroker(WebSocketDestination.SUBSCRIBE_PREFIX);
        registry.setApplicationDestinationPrefixes(WebSocketDestination.PUBLISH_PREFIX);
    }
}
