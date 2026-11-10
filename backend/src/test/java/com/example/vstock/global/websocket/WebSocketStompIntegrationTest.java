package com.example.vstock.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.dto.PriceResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.event.TradeEvent;
import com.example.vstock.domain.stock.service.LatestPriceStore;
import com.example.vstock.domain.stock.service.StockMessagePublisher;
import com.example.vstock.domain.stock.service.StockPriceListener;
import com.example.vstock.global.config.WebSocketConfig;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.transaction.annotation.EnableTransactionManagement;
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
    private StockMessagePublisher publisher;

    @Autowired
    private SubscriptionRegistry subscriptionRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private SimpleBrokerMessageHandler brokerMessageHandler;

    @Test
    void 구독한_종목_체결가_경로로_발행하면_메시지를_수신한다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceMessage> received =
                subscribe(session, WebSocketDestination.stockPrice(1L), PriceMessage.class);

        PriceMessage message = awaitFirst(
                received, () -> publisher.publishPrice(1L, new PriceMessage(1L, 10_500L)));

        assertThat(message.stockId()).isEqualTo(1L);
        assertThat(message.price()).isEqualTo(10_500L);
        session.disconnect();
    }

    @Test
    void 호가_경로도_같은_방식으로_수신한다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceMessage> received =
                subscribe(session, WebSocketDestination.stockOrderbook(1L), PriceMessage.class);

        PriceMessage message = awaitFirst(
                received, () -> publisher.publishOrderbook(1L, new PriceMessage(1L, 9_900L)));

        assertThat(message.price()).isEqualTo(9_900L);
        session.disconnect();
    }

    @Test
    void 체결가와_호가_경로는_서로_섞이지_않는다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceMessage> fromPrice =
                subscribe(session, WebSocketDestination.stockPrice(1L), PriceMessage.class);
        BlockingQueue<PriceMessage> fromOrderbook =
                subscribe(session, WebSocketDestination.stockOrderbook(1L), PriceMessage.class);

        awaitFirst(fromOrderbook, () -> publisher.publishOrderbook(1L, new PriceMessage(1L, 9_900L)));

        assertThat(fromPrice.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
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
        BlockingQueue<PriceMessage> fromStock1 =
                subscribe(session, WebSocketDestination.stockPrice(1L), PriceMessage.class);
        BlockingQueue<PriceMessage> fromStock2 =
                subscribe(session, WebSocketDestination.stockPrice(2L), PriceMessage.class);

        PriceMessage onStock2 = awaitFirst(
                fromStock2, () -> publisher.publishPrice(2L, new PriceMessage(2L, 20_000L)));

        assertThat(onStock2.stockId()).isEqualTo(2L);
        assertThat(fromStock1.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void 체결이_발생하면_구독자에게_바뀐_현재가가_푸시된다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceResponse> received =
                subscribe(session, WebSocketDestination.stockPrice(1L), PriceResponse.class);
        Instant executedAt = Instant.parse("2026-10-27T01:00:00Z");

        PriceResponse message = awaitFirst(received, () -> eventPublisher.publishEvent(
                new TradeEvent(1L, 10_700L, 3L, executedAt)));

        assertThat(message.stockId()).isEqualTo(1L);
        assertThat(message.price()).isEqualTo(10_700L);
        assertThat(message.quantity()).isEqualTo(3L);
        assertThat(message.executedAt()).isEqualTo(executedAt.toEpochMilli());
        session.disconnect();
    }

    @Test
    void 연결을_끊으면_세션이_닫히고_이후_체결이_발생해도_서버는_정상이다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceResponse> received =
                subscribe(session, WebSocketDestination.stockPrice(3L), PriceResponse.class);
        awaitFirst(received, () -> eventPublisher.publishEvent(
                new TradeEvent(3L, 10_000L, 1L, Instant.parse("2026-10-27T01:00:00Z"))));

        session.disconnect();

        assertThat(session.isConnected()).isFalse();
        received.clear();
        eventPublisher.publishEvent(new TradeEvent(3L, 10_100L, 1L, Instant.parse("2026-10-27T01:00:01Z")));
        assertThat(received.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();

        // 끊긴 세션이 남아 있지 않다면 새 연결은 정상적으로 같은 경로를 구독해 수신한다.
        StompSession reconnected = connect();
        BlockingQueue<PriceResponse> again =
                subscribe(reconnected, WebSocketDestination.stockPrice(3L), PriceResponse.class);
        PriceResponse message = awaitFirst(again, () -> eventPublisher.publishEvent(
                new TradeEvent(3L, 10_200L, 1L, Instant.parse("2026-10-27T01:00:02Z"))));
        assertThat(message.price()).isEqualTo(10_200L);
        reconnected.disconnect();
    }

    @Test
    void 구독을_해제하면_더_이상_수신하지_않는다() throws Exception {
        StompSession session = connect();
        BlockingQueue<PriceMessage> received = new LinkedBlockingQueue<>();
        StompSession.Subscription subscription = session.subscribe(
                WebSocketDestination.stockPrice(4L), new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return PriceMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.add((PriceMessage) payload);
                    }
                });
        awaitFirst(received, () -> publisher.publishPrice(4L, new PriceMessage(4L, 1L)));

        subscription.unsubscribe();
        // UNSUBSCRIBE가 처리된 뒤에 발행되도록 같은 세션에서 다른 구독을 하나 더 확인한다.
        BlockingQueue<PriceMessage> marker =
                subscribe(session, WebSocketDestination.stockPrice(5L), PriceMessage.class);
        awaitFirst(marker, () -> publisher.publishPrice(5L, new PriceMessage(5L, 1L)));
        received.clear();
        publisher.publishPrice(4L, new PriceMessage(4L, 2L));

        assertThat(received.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void 클라이언트가_시세_경로로_직접_발행하면_구독자에게_전달되지_않는다() throws Exception {
        StompSession subscriber = connect();
        BlockingQueue<PriceMessage> received =
                subscribe(subscriber, WebSocketDestination.stockPrice(6L), PriceMessage.class);
        BlockingQueue<PriceMessage> marker =
                subscribe(subscriber, WebSocketDestination.stockPrice(7L), PriceMessage.class);
        awaitFirst(marker, () -> publisher.publishPrice(7L, new PriceMessage(7L, 1L)));

        StompSession attacker = connect();
        attacker.send(WebSocketDestination.stockPrice(6L), new PriceMessage(6L, 1L));

        assertThat(received.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
        subscriber.disconnect();
    }

    @Test
    void 구독한_간격의_봉은_모아서_보내고_구독하지_않은_간격은_받지_않는다() throws Exception {
        StompSession session = connect();
        BlockingQueue<CandleResponse> oneMinute =
                subscribe(session, WebSocketDestination.stockCandle(8L, "1m"), CandleResponse.class);
        CandleResponse candle = new CandleResponse(1_793_059_200L, 100, 120, 90, 110, 7, false, 3);

        CandleResponse received = awaitFirst(oneMinute, () -> {
            publisher.publishCandle(8L, CandleInterval.TEN_MINUTES, candle);
            publisher.publishCandle(8L, CandleInterval.ONE_MINUTE, candle);
        });

        assertThat(received.time()).isEqualTo(1_793_059_200L);
        assertThat(received.close()).isEqualTo(110L);
        assertThat(received.finalized()).isFalse();
        session.disconnect();
    }

    @Test
    void 연결을_끊으면_그_연결의_봉_구독이_정리된다() throws Exception {
        String destination = WebSocketDestination.stockCandle(9L, "1m");
        StompSession session = connect();
        BlockingQueue<CandleResponse> received =
                subscribe(session, destination, CandleResponse.class);
        awaitFirst(received, () -> publisher.publishCandle(9L, CandleInterval.ONE_MINUTE,
                new CandleResponse(1_793_059_200L, 100, 120, 90, 110, 7, true, 3)));
        assertThat(subscriptionRegistry.hasSubscribers(destination)).isTrue();

        session.disconnect();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (subscriptionRegistry.hasSubscribers(destination) && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        assertThat(subscriptionRegistry.hasSubscribers(destination)).isFalse();
    }

    @Test
    void 응답하지_않는_연결을_정리하도록_하트비트가_설정돼_있다() {
        assertThat(brokerMessageHandler.getHeartbeatValue())
                .containsExactly(WebSocketConfig.HEARTBEAT_MILLIS, WebSocketConfig.HEARTBEAT_MILLIS);
        assertThat(brokerMessageHandler.getTaskScheduler()).isNotNull();
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
    @EnableScheduling
    @EnableTransactionManagement
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import({
            WebSocketConfig.class,
            StompAuthChannelInterceptor.class,
            StompEventListener.class,
            SubscriptionRegistry.class,
            WebSocketErrorHandler.class,
            StockMessagePublisher.class,
            LatestPriceStore.class,
            StockPriceListener.class
    })
    static class SocketOnlyApp {
    }
}
