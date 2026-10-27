package com.example.vstock.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.global.config.WebSocketConfig;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 소켓 기본 설정 검증. 연결 → 구독 → 수신까지 실제 서버를 띄워 확인한다.
 *
 * 차트·호가창 기능이 아직 없어도 설정만 검증할 수 있도록 DB 자동설정은 제외하고 띄운다.
 * (다른 테스트와 달리 로컬 MySQL이 필요하지 않다.)
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WebSocketStompIntegrationTest.SocketOnlyApp.class,
        properties = {
                "spring.profiles.active=test",
                "vstock.websocket.allowed-origins=http://localhost:3000"
        }
)
class WebSocketStompIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5L;
    private static final long POLL_MILLIS = 200L;
    private static final long SETTLE_MILLIS = 500L;

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketMessageSender messageSender;

    @Test
    void 구독한_종목_체결가_경로로_발행하면_메시지를_수신한다() throws Exception {
        StompSession session = connect();
        String destination = WebSocketDestination.stockPrice(1L);
        BlockingQueue<PriceMessage> received = subscribe(session, destination, PriceMessage.class);

        PriceMessage message = awaitFirst(
                received, () -> messageSender.send(destination, new PriceMessage(1L, 10_500L)));

        assertThat(message.stockId()).isEqualTo(1L);
        assertThat(message.price()).isEqualTo(10_500L);
        session.disconnect();
    }

    @Test
    void 호가_경로도_같은_방식으로_수신한다() throws Exception {
        StompSession session = connect();
        String destination = WebSocketDestination.stockOrderbook(1L);
        BlockingQueue<PriceMessage> received = subscribe(session, destination, PriceMessage.class);

        PriceMessage message = awaitFirst(
                received, () -> messageSender.send(destination, new PriceMessage(1L, 9_900L)));

        assertThat(message.price()).isEqualTo(9_900L);
        session.disconnect();
    }

    /**
     * 종목별로 구독이 격리되는지 본다.
     *
     * 구독이 브로커에 반영되기 전에 발행하면 종목과 무관하게 메시지가 버려지므로,
     * 종목 2 수신을 먼저 확인해 두 SUBSCRIBE가 모두 등록됐음을 보장한 뒤에 종목 1을 검사한다.
     * (SUBSCRIBE는 같은 세션에서 순서대로 전송되므로 먼저 보낸 종목 1도 등록된 상태다.)
     */
    @Test
    void 다른_종목으로_발행한_메시지는_수신하지_않는다() throws Exception {
        StompSession session = connect();
        String stock1 = WebSocketDestination.stockPrice(1L);
        String stock2 = WebSocketDestination.stockPrice(2L);
        BlockingQueue<PriceMessage> fromStock1 = subscribe(session, stock1, PriceMessage.class);
        BlockingQueue<PriceMessage> fromStock2 = subscribe(session, stock2, PriceMessage.class);

        PriceMessage onStock2 = awaitFirst(
                fromStock2, () -> messageSender.send(stock2, new PriceMessage(2L, 20_000L)));

        assertThat(onStock2.stockId()).isEqualTo(2L);
        assertThat(fromStock1.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
        session.disconnect();
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        return stompClient.connectAsync(
                        "ws://localhost:" + port + WebSocketConfig.HANDSHAKE_ENDPOINT,
                        new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private <T> BlockingQueue<T> subscribe(
            StompSession session, String destination, Class<T> payloadType) {

        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((T) payload);
            }
        });
        return messages;
    }

    /**
     * SUBSCRIBE가 브로커에 반영되기 전에 발행하면 메시지가 버려지므로, 수신될 때까지 재발행한다.
     */
    private <T> T awaitFirst(BlockingQueue<T> messages, Runnable publish) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            publish.run();
            T message = messages.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
        throw new AssertionError("제한 시간 안에 메시지를 수신하지 못했습니다.");
    }

    record PriceMessage(Long stockId, Long price) {
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, WebSocketMessageSender.class})
    static class SocketOnlyApp {
    }
}
