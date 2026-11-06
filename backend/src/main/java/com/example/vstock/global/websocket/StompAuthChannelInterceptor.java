package com.example.vstock.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 클라이언트가 보내는 STOMP 프레임을 검사하는 자리.
 *
 * <ul>
 *   <li>SEND: {@link WebSocketDestination#PUBLISH_PREFIX} 경로만 허용한다. 막지 않으면 클라이언트가
 *       {@code /topic/stocks/1/price}로 직접 보내 다른 구독자에게 가짜 시세를 뿌릴 수 있다.
 *       서버가 {@code SimpMessagingTemplate}으로 내보내는 메시지는 이 채널을 거치지 않아 영향이 없다.</li>
 *   <li>CONNECT: 인증 정보를 꺼내 세션 principal로 심는 자리. <b>아직 인증을 하지 않는다.</b>
 *       로그인/회원가입(#5)이 구현되지 않아 토큰 검증 로직이 없고, 차트·호가창 구독은 비로그인 대상이라
 *       현재는 통과시킨다. 인증이 필요한 구독을 추가하기 전에 아래 TODO를 반드시 채워야 한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            rejectSendOutsideApplicationPrefix(accessor);
        } else if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // TODO(#5): 토큰을 검증해 accessor.setUser(principal)로 심는다. 실패하면 예외를 던져 연결을 끊는다.
            boolean hasToken = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER) != null;
            log.debug("STOMP CONNECT 수신. sessionId={}, 토큰 {}",
                    accessor.getSessionId(), hasToken ? "있음" : "없음");
        }

        return message;
    }

    private void rejectSendOutsideApplicationPrefix(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(WebSocketDestination.PUBLISH_PREFIX + "/")) {
            throw new MessageDeliveryException(
                    "클라이언트는 " + WebSocketDestination.PUBLISH_PREFIX + " 경로로만 발행할 수 있습니다.");
        }
    }
}
