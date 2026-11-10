package com.example.vstock.domain.stock.dto;

import java.util.List;

/**
 * 차트 조회 응답. Page가 아니라 시간 커서를 쓴다. 차트는 "N번째 페이지"가 아니라
 * "이 시각 이전 N개"를 요청하고, 새 봉이 계속 생기는 중에는 offset 페이징이 밀리기 때문이다.
 *
 * @param candles    시간 오름차순. 마지막 봉이 진행 중이면 {@code finalized=false}
 * @param nextBefore 더 과거의 봉이 있으면 응답에서 가장 오래된 봉의 시각(epoch 초).
 *                   다음 요청의 {@code before}로 넘기면 이어서 받는다. 더 없으면 null
 */
public record FindCandleListResponse(String interval, List<CandleResponse> candles, Long nextBefore) {
}
