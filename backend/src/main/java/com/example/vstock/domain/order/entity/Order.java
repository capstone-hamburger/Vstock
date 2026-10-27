package com.example.vstock.domain.order.entity;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 주문과 봇 주문을 함께 저장한다. traderType에 따라 user 또는 bot 중 하나만 채워진다.
 */
@Getter
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_stock_id_status", columnList = "stock_id, status"),
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_bot_id", columnList = "bot_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private TraderType traderType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id")
    private Bot bot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private OrderStatus status;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int filledQuantity = 0;

    // 지정가
    @Column(nullable = false)
    private Long price;

    private Order(TraderType traderType, User user, Bot bot, Stock stock,
                  OrderType type, int quantity, Long price) {
        this.traderType = traderType;
        this.user = user;
        this.bot = bot;
        this.stock = stock;
        this.type = type;
        this.status = OrderStatus.PENDING;
        this.quantity = quantity;
        this.price = price;
    }

    public static Order ofUser(User user, Stock stock, OrderType type, int quantity, Long price) {
        requireNonNull(user, "user");
        validateCommon(stock, type, price);
        return new Order(TraderType.USER, user, null, stock, type, quantity, price);
    }

    public static Order ofBot(Bot bot, Stock stock, OrderType type, int quantity, Long price) {
        requireNonNull(bot, "bot");
        validateCommon(stock, type, price);
        return new Order(TraderType.BOT, null, bot, stock, type, quantity, price);
    }

    @PrePersist
    private void validateTrader() {
        boolean valid = switch (traderType) {
            case USER -> user != null && bot == null;
            case BOT -> bot != null && user == null;
        };
        if (!valid) {
            throw new IllegalStateException(
                    "traderType과 주문자가 일치하지 않습니다. traderType=" + traderType);
        }
    }

    private static void validateCommon(Stock stock, OrderType type, Long price) {
        requireNonNull(stock, "stock");
        requireNonNull(type, "type");
        requireNonNull(price, "price");
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은(는) null일 수 없습니다.");
        }
    }
}
