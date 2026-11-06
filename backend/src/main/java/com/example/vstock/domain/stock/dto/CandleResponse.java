package com.example.vstock.domain.stock.dto;

/**
 * 봉 1개. REST 조회와 소켓 푸시가 같은 형태를 쓴다.
 *
 * @param time      봉 시작 시각 (epoch 초)
 * @param finalized 마감된 봉이면 true, 진행 중이면 false
 * @param seq       종목별 갱신 순번. 진행 중인 봉이 아니거나 DB에서 읽은 봉은 0이다.
 *                  클라이언트는 REST 응답보다 작거나 같은 seq의 소켓 메시지를 버린다.
 */
public record CandleResponse(
        long time,
        long open,
        long high,
        long low,
        long close,
        long volume,
        boolean finalized,
        long seq
) {
}
