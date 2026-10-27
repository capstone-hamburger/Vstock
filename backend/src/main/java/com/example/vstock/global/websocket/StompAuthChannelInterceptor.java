package com.example.vstock.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * CONNECT 프레임에서 인증 정보를 꺼내 세션 principal로 심는 자리.
 *
 * <p><b>아직 인증을 하지 않는다.</b> 로그인/회원가입(#5)이 구현되지 않아 토큰 검증 로직이 없고,
 * 차트·호가창 구독은 비로그인 대상이라 현재는 통과시킨다. 인증이 필요한 구독을 추가하기 전에
 * 아래 TODO를 반드시 채워야 한다.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        // TODO(#5): 토큰을 검증해 accessor.setUser(principal)로 심는다. 실패하면 예외를 던져 연결을 끊는다.
        boolean hasToken = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER) != null;
        log.debug("STOMP CONNECT 수신. sessionId={}, 토큰 {}",
                accessor.getSessionId(), hasToken ? "있음" : "없음");

        return message;
    }
}
