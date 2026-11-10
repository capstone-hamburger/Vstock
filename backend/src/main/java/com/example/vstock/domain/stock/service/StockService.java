package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.dto.FindCandleListResponse;
import com.example.vstock.domain.stock.dto.StockResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StockService {

    public static final int DEFAULT_CANDLE_LIMIT = 300;
    public static final int MAX_CANDLE_LIMIT = 1000;

    private final StockRepository stockRepository;
    private final LatestPriceStore latestPriceStore;
    private final StockCandleRepository candleRepository;
    private final CandleAggregator candleAggregator;
    private final Clock clock;

    /**
     * 종목 정보와 현재가. 현재가는 메모리의 최근 체결가를 우선하고, 없으면 DB에 저장된 값을 쓴다.
     * 소켓으로 받는 시세와 같은 기준이라, 이 응답을 받은 뒤 구독을 시작해도 값이 어긋나지 않는다.
     */
    public StockResponse findStockById(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다. stockId=" + stockId));

        long currentPrice = latestPriceStore.findPrice(stockId).orElse(stock.getCurrentPrice());
        return StockResponse.of(stock, currentPrice);
    }

    /**
     * 요청한 간격으로 저장된 봉을 최신 limit개 읽고, 조회 구간이 현재까지 닿아 있으면 메모리의
     * 진행 중인 봉을 마지막에 붙여 시간 오름차순으로 돌려준다. 집계는 하지 않는다.
     * 체결이 없던 구간은 봉이 없다.
     *
     * @param from   이 시각(epoch 초) 이후의 봉만. null이면 제한 없음
     * @param before 이 시각(epoch 초) 이전에 시작한 봉만. null이면 지금까지
     * @param limit  null이면 {@value #DEFAULT_CANDLE_LIMIT}, 최대 {@value #MAX_CANDLE_LIMIT}
     */
    public FindCandleListResponse findCandles(Long stockId, String intervalCode,
                                              Long from, Long before, Integer limit) {
        CandleInterval interval = parseInterval(intervalCode);
        int size = resolveLimit(limit);
        if (!stockRepository.existsById(stockId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다. stockId=" + stockId);
        }

        long fromEpoch = from == null ? 0L : from;
        long beforeEpoch = before == null ? clock.instant().getEpochSecond() + 1 : before;
        if (fromEpoch >= beforeEpoch) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from은 before보다 이전이어야 합니다.");
        }

        CandleResponse live = findLiveCandleInRange(stockId, interval, fromEpoch, beforeEpoch);
        int storedLimit = live == null ? size : size - 1;

        // 한 개 더 읽어서 더 과거의 봉이 남았는지 확인한다.
        List<StockCandle> rows = candleRepository.findCandlesBefore(
                stockId, interval, toLocalDateTime(fromEpoch), toLocalDateTime(beforeEpoch),
                PageRequest.of(0, storedLimit + 1));
        boolean hasMore = rows.size() > storedLimit;

        List<CandleResponse> candles = new ArrayList<>();
        for (StockCandle row : rows.subList(0, Math.min(rows.size(), storedLimit))) {
            candles.add(toResponse(row));
        }
        // 조회는 최신순이므로 차트가 쓰는 오름차순으로 뒤집는다.
        Collections.reverse(candles);
        if (live != null) {
            candles.removeIf(candle -> candle.time() == live.time());
            candles.add(live);
        }

        Long nextBefore = hasMore ? candles.get(0).time() : null;
        return new FindCandleListResponse(interval.getCode(), candles, nextBefore);
    }

    // 조회 구간이 현재까지 닿아 있을 때만 진행 중인 봉을 붙인다. 과거 구간을 볼 때는 붙이지 않는다.
    private CandleResponse findLiveCandleInRange(Long stockId, CandleInterval interval,
                                                 long fromEpoch, long beforeEpoch) {
        CandleResponse live = candleAggregator.findCurrentCandle(stockId, interval);
        if (live == null || live.time() < fromEpoch || live.time() >= beforeEpoch) {
            return null;
        }
        return live;
    }

    private CandleInterval parseInterval(String intervalCode) {
        try {
            return CandleInterval.fromCode(intervalCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CANDLE_LIMIT;
        }
        if (limit < 1 || limit > MAX_CANDLE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit은 1 이상 " + MAX_CANDLE_LIMIT + " 이하여야 합니다. limit=" + limit);
        }
        return limit;
    }

    private LocalDateTime toLocalDateTime(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), MarketTime.ZONE);
    }

    private CandleResponse toResponse(StockCandle candle) {
        return new CandleResponse(MarketTime.toEpochSecond(candle.getTimestamp()),
                candle.getOpenPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getClosePrice(),
                candle.getVolume(), true, 0);
    }
}
