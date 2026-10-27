package com.example.vstock.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * STOMP 처리 중 발생한 예외를 로깅한 뒤 ERROR 프레임으로 내려보낸다.
 *
 * 기본 핸들러는 조용히 ERROR 프레임만 보내서 서버 로그에 아무것도 남지 않는다.
 * 소켓 문제는 재현이 어려우므로 최소한 서버 쪽 기록은 남겨 둔다.
 */
@Slf4j
@Component
public class WebSocketErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage, Throwable ex) {

        log.warn("STOMP 메시지 처리 실패: {}", ex.getMessage(), ex);
        return super.handleClientMessageProcessingError(clientMessage, ex);
    }

    @Override
    public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage) {
        log.warn("STOMP ERROR 프레임 전송");
        return super.handleErrorMessageToClient(errorMessage);
    }
}
