package com.example.vstock.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 구독자에게 메시지를 발행한다. 기능 코드가 {@link SimpMessagingTemplate}을 직접 다루지 않게 하는 얇은 래퍼.
 *
 * 경로는 {@link WebSocketDestination}으로 만들어 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 구독자가 없으면 메시지는 예외 없이 그대로 버려진다.
     *
     * payload는 후속 기능에서 차트·호가창 DTO로 구체화한다.
     */
    public void send(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
