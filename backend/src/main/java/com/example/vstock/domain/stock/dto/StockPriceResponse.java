package com.example.vstock.domain.stock.dto;

import com.example.vstock.domain.stock.entity.Stock;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class StockPriceResponse {

    private final Long stockId;
    private final String name;
    private final Long currentPrice;

    public static StockPriceResponse from(Stock stock) {
        return new StockPriceResponse(stock.getStockId(), stock.getName(), stock.getCurrentPrice());
    }
}
