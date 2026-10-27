package com.example.vstock.global.websocket;

/**
 * 소켓 구독·발행 경로. 경로 문자열은 여기서만 만들고 기능 코드에서 하드코딩하지 않는다.
 *
 * 종목 상세 화면은 진입 시 REST로 스냅샷을 받고, 이후 아래 경로를 구독해 변경분만 수신한다.
 */
public final class WebSocketDestination {

    /** 클라이언트가 구독하는 경로의 prefix. 브로커가 처리한다. */
    public static final String SUBSCRIBE_PREFIX = "/topic";

    /** 클라이언트가 서버로 보내는 경로의 prefix. {@code @MessageMapping}이 처리한다. */
    public static final String PUBLISH_PREFIX = "/app";

    private static final String STOCK_PRICE = SUBSCRIBE_PREFIX + "/stocks/%d/price";
    private static final String STOCK_ORDERBOOK = SUBSCRIBE_PREFIX + "/stocks/%d/orderbook";

    private WebSocketDestination() {
    }

    /** 종목의 최신 체결가 경로. */
    public static String stockPrice(Long stockId) {
        return format(STOCK_PRICE, stockId);
    }

    /** 종목의 호가 변동 경로. */
    public static String stockOrderbook(Long stockId) {
        return format(STOCK_ORDERBOOK, stockId);
    }

    private static String format(String template, Long stockId) {
        if (stockId == null) {
            throw new IllegalArgumentException("stockId은(는) null일 수 없습니다.");
        }
        return String.format(template, stockId);
    }
}
