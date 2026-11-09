package com.example.vstock.domain.stock.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 종목별 가장 최근 체결가를 메모리에 둔다. 체결마다 DB를 UPDATE하지 않기 위한 저장소다.
 * 서버가 재시작되면 비어 있고, 그동안은 DB에 저장된 값이 현재가다.
 */
@Component
public class LatestPriceStore {

    private final Map<Long, LatestPrice> prices = new ConcurrentHashMap<>();

    /**
     * 더 오래된 체결은 반영하지 않는다. 순서가 뒤바뀌어 도착한 체결이 현재가를 되돌리지 않게 하려는 것이다.
     *
     * @return 현재가가 갱신됐으면 true
     */
    public boolean update(Long stockId, long price, Instant executedAt) {
        boolean[] applied = {false};
        prices.compute(stockId, (id, current) -> {
            if (current != null && executedAt.isBefore(current.executedAt())) {
                return current;
            }
            applied[0] = true;
            return new LatestPrice(price, executedAt);
        });
        return applied[0];
    }

    public Optional<Long> findPrice(Long stockId) {
        return Optional.ofNullable(prices.get(stockId)).map(LatestPrice::price);
    }

    private record LatestPrice(long price, Instant executedAt) {
    }
}
