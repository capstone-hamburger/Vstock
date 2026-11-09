package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 시 종목별 최근 체결가를 저장된 마지막 1분봉의 종가로 되살린다.
 *
 * <p>{@link LatestPriceStore}는 메모리라 재시작하면 비어 있고, 그동안 현재가는 {@code stocks.current_price}가
 * 되는데 이 값은 체결로 갱신되지 않아 시드값 그대로다. 마지막 봉의 종가와 그 체결 시각으로 채워 두면
 * 재시작 직후에도 마지막 체결가가 나온다. 체결 시각을 함께 넣어서, 이후 실제 체결은 이 값을 덮어쓰고
 * 이 값보다 오래된 체결이 늦게 와도 현재가를 되돌리지 못한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LatestPriceInitializer {

    private final StockRepository stockRepository;
    private final StockCandleRepository candleRepository;
    private final LatestPriceStore latestPriceStore;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            for (Stock stock : stockRepository.findAll()) {
                candleRepository
                        .findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(
                                stock.getStockId(), CandleInterval.ONE_MINUTE)
                        .ifPresent(candle -> latestPriceStore.update(stock.getStockId(), candle.getClosePrice(),
                                Instant.ofEpochMilli(candle.getLastTradeAt())));
            }
        } catch (RuntimeException e) {
            // 실패해도 기동은 막지 않는다. 첫 체결 전까지 현재가는 DB 값으로 나간다.
            log.error("최근 체결가 복구에 실패했다.", e);
        }
    }
}
