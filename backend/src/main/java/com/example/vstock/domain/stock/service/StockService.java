package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.entity.CandleInterval;
import com.example.vstock.domain.stock.entity.StockCandle;
import com.example.vstock.domain.stock.repository.StockCandleRepository;
import com.example.vstock.domain.stock.repository.StockRepository;
import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final StockCandleRepository candleRepository;
    private final CandleAggregator candleAggregator;
    private final Clock clock;

    /**
     * 저장된 1분봉을 interval에 맞게 집계하고, 메모리의 진행 중인 봉을 마지막에 붙여 시간 오름차순으로 돌려준다.
     * 체결이 없던 구간은 봉을 만들지 않는다.
     *
     * @param from  null이면 to에서 limit개 봉만큼 거슬러 올라간 시각
     * @param to    null이면 지금
     * @param limit null이면 {@value #DEFAULT_CANDLE_LIMIT}, 최대 {@value #MAX_CANDLE_LIMIT}
     */
    public List<CandleResponse> findCandles(Long stockId, String intervalCode,
                                            OffsetDateTime from, OffsetDateTime to, Integer limit) {
        CandleInterval interval = parseInterval(intervalCode);
        int size = resolveLimit(limit);
        if (!stockRepository.existsById(stockId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다. stockId=" + stockId);
        }

        LocalDateTime end = to == null
                ? LocalDateTime.now(clock).plusSeconds(1)
                : to.atZoneSameInstant(MarketTime.ZONE).toLocalDateTime();
        LocalDateTime begin = from == null
                ? end.minusSeconds((long) interval.getSeconds() * size)
                : from.atZoneSameInstant(MarketTime.ZONE).toLocalDateTime();
        if (!begin.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from은 to보다 이전이어야 합니다.");
        }
        begin = interval.bucketStart(begin);

        List<CandleResponse> candles = findStoredCandles(stockId, interval, begin, end, size);
        mergeCurrentCandle(candles, stockId, interval, begin, end);
        return candles;
    }

    private List<CandleResponse> findStoredCandles(Long stockId, CandleInterval interval,
                                                   LocalDateTime from, LocalDateTime to, int limit) {
        List<CandleResponse> candles = new ArrayList<>();
        if (interval == CandleInterval.ONE_MINUTE) {
            for (StockCandle candle : candleRepository.findLatestCandles(stockId, from, to, PageRequest.of(0, limit))) {
                candles.add(toResponse(candle));
            }
        } else {
            for (Object[] row : candleRepository.findAggregatedCandles(stockId, from, to, interval.getSeconds(), limit)) {
                candles.add(toResponse(row));
            }
        }
        // 조회는 최신순이므로 차트가 쓰는 오름차순으로 뒤집는다.
        Collections.reverse(candles);
        return candles;
    }

    // 진행 중인 봉은 DB 집계보다 정확하므로 같은 시작 시각의 봉을 교체한다.
    private void mergeCurrentCandle(List<CandleResponse> candles, Long stockId, CandleInterval interval,
                                    LocalDateTime from, LocalDateTime to) {
        CandleResponse live = candleAggregator.findCurrentCandle(stockId, interval);
        if (live == null
                || live.time() < MarketTime.toEpochSecond(from)
                || live.time() >= MarketTime.toEpochSecond(to)) {
            return;
        }
        candles.removeIf(candle -> candle.time() == live.time());
        candles.add(live);
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

    private CandleResponse toResponse(StockCandle candle) {
        return new CandleResponse(MarketTime.toEpochSecond(candle.getStartTime()),
                candle.getOpenPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getClosePrice(),
                candle.getVolume(), true, 0);
    }

    // findAggregatedCandles의 컬럼 순서: bucket_epoch, open, high, low, close, volume
    private CandleResponse toResponse(Object[] row) {
        LocalDateTime start = LocalDateTime.ofEpochSecond(((Number) row[0]).longValue(), 0, ZoneOffset.UTC);
        return new CandleResponse(MarketTime.toEpochSecond(start),
                ((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(), ((Number) row[4]).longValue(),
                ((Number) row[5]).longValue(), true, 0);
    }
}
