package com.example.vstock.domain.stock.dto;

import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;

/**
 * 종목 기본 정보와 현재가.
 *
 * @param currentPrice 가장 최근 체결가. 서버가 켜진 뒤 체결이 한 번도 없었다면 DB에 저장된 값이다.
 */
public record StockResponse(Long stockId, String name, Sector sector, long currentPrice) {

    public static StockResponse of(Stock stock, long currentPrice) {
        return new StockResponse(stock.getStockId(), stock.getName(), stock.getSector(), currentPrice);
    }
}
