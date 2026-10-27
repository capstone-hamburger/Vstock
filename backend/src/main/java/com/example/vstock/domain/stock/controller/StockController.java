package com.example.vstock.domain.stock.controller;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.service.StockService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * 종목 차트. 마지막 봉이 진행 중이면 {@code finalized=false}로 내려가며, 이후 변경분은
     * {@code /topic/stocks/{stockId}/candles/{interval}} 구독으로 받는다.
     */
    @GetMapping("/stocks/{stockId}/candles")
    public List<CandleResponse> findCandles(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Integer limit) {
        return stockService.findCandles(stockId, interval, from, to, limit);
    }
}
