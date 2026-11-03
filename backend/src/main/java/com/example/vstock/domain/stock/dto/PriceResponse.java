package com.example.vstock.domain.stock.dto;

/**
 * 체결 1건으로 바뀐 현재가. {@code /topic/stocks/{stockId}/price}로 푸시한다.
 *
 * @param executedAt 체결 시각 (epoch 밀리초)
 */
public record PriceResponse(Long stockId, long price, long quantity, long executedAt) {
}
