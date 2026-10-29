package com.example.vstock.domain.stock.controller;

import com.example.vstock.domain.stock.dto.StockPriceResponse;
import com.example.vstock.domain.stock.service.StockService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/{stockId}")
    public StockPriceResponse findStockPrice(@PathVariable @Positive Long stockId) {
        return stockService.findStockPrice(stockId);
    }
}
