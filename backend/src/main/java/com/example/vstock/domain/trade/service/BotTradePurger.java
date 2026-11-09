package com.example.vstock.domain.trade.service;

import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.domain.trade.repository.TradeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 봇끼리의 체결을 주기적으로 지운다. 회원이 낀 체결은 기록이라 지우지 않는다.
 *
 * <p>봇 체결을 남겨 두는 이유는 서버가 재시작될 때 아직 저장되지 않은 1분봉을 다시 만들기 위해서다.
 * 그래서 이미 저장된 1분봉에 반영된 체결만 지운다. 종목별 삭제 기준은 아래 두 시각 중 이른 쪽이다.
 *
 * <ul>
 *   <li><b>저장된 마지막 1분봉의 끝</b>: 서버가 한동안 꺼졌다 켜져도 아직 봉이 되지 못한 체결은 남는다.
 *       현재 시각만 기준으로 하면 이 체결까지 지워질 수 있다.</li>
 *   <li><b>현재 시각 - 보관 시간</b>: 중간 봉의 저장이 실패했을 때 그 구간의 체결로 봉을 다시 만들 여유다.</li>
 * </ul>
 *
 * 저장된 1분봉이 없는 종목은 지우지 않는다.
 */
@Slf4j
@Component
public class BotTradePurger {

    private final StockRepository stockRepository;
    private final StockCandleRepository candleRepository;
    private final TradeRepository tradeRepository;
    private final Clock clock;
    private final long retentionMinutes;

    public BotTradePurger(StockRepository stockRepository, StockCandleRepository candleRepository,
                          TradeRepository tradeRepository, Clock clock,
                          @Value("${vstock.trade.bot-retention-minutes:5}") long retentionMinutes) {
        if (retentionMinutes < 1) {
            throw new IllegalArgumentException("보관 시간은 1분 이상이어야 합니다. retentionMinutes=" + retentionMinutes);
        }
        this.stockRepository = stockRepository;
        this.candleRepository = candleRepository;
        this.tradeRepository = tradeRepository;
        this.clock = clock;
        this.retentionMinutes = retentionMinutes;
    }

    /**
     * 모든 종목의 봇 체결을 지운다. 한 종목이 실패해도 나머지는 계속한다.
     *
     * @return 지운 행 수 합계
     */
    @Scheduled(fixedDelayString = "${vstock.trade.purge-interval-millis:60000}",
            initialDelayString = "${vstock.trade.purge-interval-millis:60000}")
    public int purge() {
        LocalDateTime now = LocalDateTime.now(clock);
        int total = 0;
        for (Stock stock : stockRepository.findAll()) {
            try {
                total += purge(stock.getStockId(), now);
            } catch (RuntimeException e) {
                log.error("봇 체결 정리에 실패했다. stockId={}", stock.getStockId(), e);
            }
        }
        if (total > 0) {
            log.info("봇 체결 {}건을 정리했다.", total);
        }
        return total;
    }

    private int purge(Long stockId, LocalDateTime now) {
        Optional<LocalDateTime> cutoff = findCutoff(stockId, now);
        return cutoff.map(before -> tradeRepository.deleteBotOnlyTradesBefore(stockId, before)).orElse(0);
    }

    /** 이 시각보다 이전의 봇 체결을 지울 수 있다. 저장된 1분봉이 없으면 비어 있다. */
    Optional<LocalDateTime> findCutoff(Long stockId, LocalDateTime now) {
        LocalDateTime retentionBoundary = now.minusMinutes(retentionMinutes);
        return candleRepository
                .findFirstByStock_StockIdAndCandleIntervalOrderByTimestampDesc(stockId, CandleInterval.ONE_MINUTE)
                .map(candle -> candle.getTimestamp().plusMinutes(1))
                .map(storedBoundary -> storedBoundary.isBefore(retentionBoundary) ? storedBoundary : retentionBoundary);
    }
}
