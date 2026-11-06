package com.example.vstock.domain.trade.entity;

import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.global.common.BaseTimeEntity;
import com.example.vstock.global.common.MarketTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 체결 한 건. 엔진이 알려준 체결(가격, 수량, 시각)을 한 번 기록하면 바뀌지 않는다.
 *
 * <p>주문({@code orders})은 "무엇을 사고팔겠다는 요청과 그 상태"이고, 체결은 "실제로 성사된 거래"라서
 * 따로 둔다. 한 주문이 여러 가격으로 나뉘어 체결될 수 있기 때문이다.
 *
 * <p>구매·판매 주문은 외래키 없이 ID만 가진다. 봇끼리의 체결과 끝난 봇 주문은 주기적으로 지우는데,
 * 외래키가 있으면 회원이 낀 체결이 남아 있는 봇 주문을 지울 수 없게 된다.
 *
 * <p>엔진의 {@code tradeId}는 엔진을 재시작하면 1부터 다시 시작한다. 같은 체결을 두 번 처리하지 않도록
 * (엔진 세션, tradeId)에 unique 제약을 건다. 엔진 세션 ID가 아직 없으면 null이고, 그동안은 중복 방지가
 * 되지 않는다.
 */
@Getter
@Entity
@Table(
        name = "trades",
        indexes = {
                @Index(name = "idx_trades_stock_id_executed_at", columnList = "stock_id, executed_at"),
                @Index(name = "idx_trades_buy_order_id", columnList = "buy_order_id"),
                @Index(name = "idx_trades_sell_order_id", columnList = "sell_order_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trades_engine_trade", columnNames = {"engine_session_id", "engine_trade_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /** 체결가(원). 주문의 지정가와 다를 수 있다. */
    @Column(nullable = false)
    private Long price;

    /** 체결 수량(주). */
    @Column(nullable = false)
    private Long quantity;

    /** 체결 시각. 봉과 같은 기준({@link MarketTime#ZONE})의 현지 시각이고, 밀리초까지 저장한다. */
    @Column(nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime executedAt;

    @Column(nullable = false)
    private Long buyOrderId;

    @Column(nullable = false)
    private Long sellOrderId;

    @Column(length = 64)
    private String engineSessionId;

    @Column(nullable = false)
    private Long engineTradeId;

    private Trade(Stock stock, Long price, Long quantity, LocalDateTime executedAt, Long buyOrderId,
                  Long sellOrderId, String engineSessionId, Long engineTradeId) {
        this.stock = stock;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.engineSessionId = engineSessionId;
        this.engineTradeId = engineTradeId;
    }

    public static Trade of(Stock stock, long price, long quantity, Instant executedAt, Long buyOrderId,
                           Long sellOrderId, String engineSessionId, Long engineTradeId) {
        requireNonNull(stock, "stock");
        requireNonNull(executedAt, "executedAt");
        requireNonNull(buyOrderId, "buyOrderId");
        requireNonNull(sellOrderId, "sellOrderId");
        requireNonNull(engineTradeId, "engineTradeId");
        if (price <= 0) {
            throw new IllegalArgumentException("price는 0보다 커야 합니다. price=" + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 0보다 커야 합니다. quantity=" + quantity);
        }
        // DB는 밀리초까지만 저장하고 나머지를 반올림하므로, 저장 전에 같은 자리로 잘라 둔다.
        LocalDateTime executedAtLocal = LocalDateTime.ofInstant(executedAt, MarketTime.ZONE)
                .truncatedTo(ChronoUnit.MILLIS);
        return new Trade(stock, price, quantity, executedAtLocal, buyOrderId, sellOrderId,
                engineSessionId, engineTradeId);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은(는) null일 수 없습니다.");
        }
    }
}
