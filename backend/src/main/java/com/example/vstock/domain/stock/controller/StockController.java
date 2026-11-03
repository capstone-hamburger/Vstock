package com.example.vstock.domain.stock.controller;

import com.example.vstock.domain.stock.dto.FindCandleListResponse;
import com.example.vstock.domain.stock.dto.StockResponse;
import com.example.vstock.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * 종목 상세 화면의 최초 렌더링용 스냅샷. 이후 변경분은
     * {@code /topic/stocks/{stockId}/price} 구독으로 받는다.
     */
    @GetMapping("/stocks/{stockId}")
    public StockResponse findStock(@PathVariable Long stockId) {
        return stockService.findStockById(stockId);
    }

    /**
     * 종목 차트. {@code interval}은 {@code 1m, 10m, 1h, 1d, 1M}(분과 달은 대소문자로 구분).
     * 처음에는 {@code before} 없이 호출하고, 차트를 과거로 밀 때는 응답의 {@code nextBefore}를
     * {@code before}로 넘긴다. 마지막 봉이 진행 중이면 {@code finalized=false}로 내려가며,
     * 이후 변경분은 {@code /topic/stocks/{stockId}/candles/{interval}} 구독으로 받는다.
     *
     * @param from   이 시각(epoch 초) 이후의 봉만
     * @param before 이 시각(epoch 초) 이전에 시작한 봉만
     */
    @GetMapping("/stocks/{stockId}/candles")
    public FindCandleListResponse findCandles(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        return stockService.findCandles(stockId, interval, from, before, limit);
    }
}
