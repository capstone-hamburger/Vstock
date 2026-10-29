package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.StockPriceResponse;
import com.example.vstock.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;

    public StockPriceResponse findStockPrice(Long stockId) {
        return stockRepository.findById(stockId)
                .map(StockPriceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
    }
}
