package com.example.vstock.global.config;

import com.example.vstock.global.websocket.StompAuthChannelInterceptor;
import com.example.vstock.global.websocket.WebSocketDestination;
import com.example.vstock.global.websocket.WebSocketErrorHandler;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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

    /**
     * 서버가 보내는 하트비트와 클라이언트에게 기대하는 하트비트의 간격(ms).
     * 클라이언트가 CONNECT에서 하트비트를 협상하면(브라우저 STOMP 클라이언트는 기본 10초) 이 간격 안에
     * 신호가 없는 연결을 끊어서, 탭이 닫히거나 네트워크가 끊긴 세션이 구독자로 남지 않게 한다.
     */
    public static final long HEARTBEAT_MILLIS = 10_000L;

    private final List<String> allowedOriginPatterns;
    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final WebSocketErrorHandler errorHandler;

    // 빈으로 등록하지 않는다. TaskScheduler 빈이 하나라도 있으면 @Scheduled 작업이 이 스케줄러를 같이 쓰게 된다.
    private final ThreadPoolTaskScheduler heartbeatScheduler = createHeartbeatScheduler();

    public WebSocketConfig(
            @Value("${vstock.websocket.allowed-origins}") List<String> allowedOriginPatterns,
            StompAuthChannelInterceptor authChannelInterceptor,
            WebSocketErrorHandler errorHandler) {

        this.allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
        this.authChannelInterceptor = authChannelInterceptor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 핸드셰이크는 CORS 대상이 아니라 Origin 검사를 직접 걸어야 한다.
        registry.addEndpoint(HANDSHAKE_ENDPOINT)
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 인메모리 브로커. 서버를 여러 대로 늘리면 외부 브로커(Redis/RabbitMQ)로 교체해야 한다.
        registry.enableSimpleBroker(WebSocketDestination.SUBSCRIBE_PREFIX)
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes(WebSocketDestination.PUBLISH_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }

    private static ThreadPoolTaskScheduler createHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
