package com.example.vstock.domain.stock.service;

import com.example.vstock.domain.stock.dto.PriceResponse;
import com.example.vstock.domain.stock.event.TradeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 체결이 일어나면 현재가를 갱신하고 해당 종목 구독자에게 푸시한다.
 */
@Component
@RequiredArgsConstructor
public class StockPriceListener {

    private final LatestPriceStore latestPriceStore;
    private final StockMessagePublisher messagePublisher;

    /** 체결 트랜잭션이 있으면 커밋된 뒤에, 없으면 바로 호출된다. 롤백된 체결은 시세에 반영되지 않는다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onTrade(TradeEvent event) {
        boolean applied = latestPriceStore.update(event.stockId(), event.price(), event.executedAt());
        if (!applied) {
            return;
        }
        messagePublisher.publishPrice(event.stockId(), new PriceResponse(
                event.stockId(), event.price(), event.quantity(), event.executedAt().toEpochMilli()));
    }
}
