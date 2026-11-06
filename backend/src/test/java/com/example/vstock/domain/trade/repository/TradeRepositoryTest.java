package com.example.vstock.domain.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.trade.entity.Trade;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.global.common.MarketTime;
import com.example.vstock.support.RepositoryTestSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TradeRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    @Autowired
    private TradeRepository tradeRepository;

    private Stock stock;
    private Long userOrderId;
    private Long botBuyOrderId;
    private Long botSellOrderId;

    @BeforeEach
    void setUp() {
        stock = em.persist(new Stock("테스트종목", Sector.IT, 10_000L));
        User user = em.persist(new User("홍길동", "hong@test.com", "password", 1_000_000L, 0L));
        Bot buyer = em.persist(new Bot("BUYER_BOT", Persona.MOMENTUM, BotSide.BUYER));
        Bot seller = em.persist(new Bot("SELLER_BOT", Persona.MOMENTUM, BotSide.SELLER));
        userOrderId = em.persist(Order.ofUser(user, stock, OrderType.BUY, 10, 70_500L)).getOrderId();
        botBuyOrderId = em.persist(Order.ofBot(buyer, stock, OrderType.BUY, 10, 70_500L)).getOrderId();
        botSellOrderId = em.persist(Order.ofBot(seller, stock, OrderType.SELL, 10, 70_000L)).getOrderId();
    }

    @Test
    void 체결을_저장하고_조회한다() {
        Trade saved = tradeRepository.save(trade(NINE, 70_000L, 3L, userOrderId, botSellOrderId, "s1", 1L));
        flushAndClear();

        Trade found = tradeRepository.findById(saved.getTradeId()).orElseThrow();

        assertThat(found.getStock().getStockId()).isEqualTo(stock.getStockId());
        assertThat(found.getPrice()).isEqualTo(70_000L);
        assertThat(found.getQuantity()).isEqualTo(3L);
        assertThat(found.getBuyOrderId()).isEqualTo(userOrderId);
        assertThat(found.getSellOrderId()).isEqualTo(botSellOrderId);
        assertThat(found.getEngineSessionId()).isEqualTo("s1");
        assertThat(found.getEngineTradeId()).isEqualTo(1L);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 체결_시각은_밀리초까지_그대로_저장된다() {
        LocalDateTime executedAt = NINE.withNano(123_000_000);
        Trade saved = tradeRepository.save(trade(executedAt, 70_000L, 3L, userOrderId, botSellOrderId, "s1", 1L));
        flushAndClear();

        assertThat(tradeRepository.findById(saved.getTradeId()).orElseThrow().getExecutedAt())
                .isEqualTo(executedAt);
    }

    @Test
    void 같은_엔진_체결을_다시_저장하면_거부된다() {
        tradeRepository.save(trade(NINE, 70_000L, 3L, userOrderId, botSellOrderId, "s1", 1L));
        em.flush();

        assertThatThrownBy(() -> {
            tradeRepository.save(trade(NINE.plusSeconds(1), 70_100L, 7L, userOrderId, botSellOrderId, "s1", 1L));
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void 엔진_세션이_다르면_같은_tradeId여도_저장된다() {
        tradeRepository.save(trade(NINE, 70_000L, 3L, userOrderId, botSellOrderId, "s1", 1L));
        tradeRepository.save(trade(NINE.plusSeconds(1), 70_100L, 7L, userOrderId, botSellOrderId, "s2", 1L));
        flushAndClear();

        assertThat(tradeRepository.count()).isEqualTo(2);
    }

    @Test
    void 엔진_체결이_이미_저장됐는지_확인한다() {
        tradeRepository.save(trade(NINE, 70_000L, 3L, userOrderId, botSellOrderId, "s1", 1L));
        flushAndClear();

        assertThat(tradeRepository.existsByEngineSessionIdAndEngineTradeId("s1", 1L)).isTrue();
        assertThat(tradeRepository.existsByEngineSessionIdAndEngineTradeId("s1", 2L)).isFalse();
        assertThat(tradeRepository.existsByEngineSessionIdAndEngineTradeId("s2", 1L)).isFalse();
    }

    @Test
    void 지정한_시각_이후의_체결을_오래된_순으로_조회한다() {
        tradeRepository.save(trade(NINE.plusSeconds(30), 3L, 1L, userOrderId, botSellOrderId, "s1", 3L));
        tradeRepository.save(trade(NINE, 1L, 1L, userOrderId, botSellOrderId, "s1", 1L));
        tradeRepository.save(trade(NINE.plusSeconds(10), 2L, 1L, userOrderId, botSellOrderId, "s1", 2L));
        tradeRepository.save(trade(NINE.minusSeconds(1), 9L, 1L, userOrderId, botSellOrderId, "s1", 9L));
        flushAndClear();

        List<Trade> found = tradeRepository.findTradesSince(stock.getStockId(), NINE);

        assertThat(found).extracting(Trade::getPrice).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 다른_종목의_체결은_조회하지_않는다() {
        Stock other = em.persist(new Stock("다른종목", Sector.FINANCE, 20_000L));
        tradeRepository.save(Trade.of(other, 5L, 1L, NINE.atZone(MarketTime.ZONE).toInstant(),
                userOrderId, botSellOrderId, "s1", 1L));
        flushAndClear();

        assertThat(tradeRepository.findTradesSince(stock.getStockId(), NINE.minusDays(1))).isEmpty();
    }

    @Test
    void 봇끼리의_오래된_체결만_지우고_회원이_낀_체결은_남긴다() {
        tradeRepository.save(trade(NINE, 1L, 1L, botBuyOrderId, botSellOrderId, "s1", 1L));
        tradeRepository.save(trade(NINE.plusSeconds(1), 2L, 1L, userOrderId, botSellOrderId, "s1", 2L));
        tradeRepository.save(trade(NINE.plusSeconds(2), 3L, 1L, botBuyOrderId, userOrderId, "s1", 3L));
        flushAndClear();

        int deleted = tradeRepository.deleteBotOnlyTradesBefore(stock.getStockId(), NINE.plusMinutes(1));
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(tradeRepository.findAll()).extracting(Trade::getPrice).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void 기준_시각_이후의_봇_체결은_지우지_않는다() {
        tradeRepository.save(trade(NINE, 1L, 1L, botBuyOrderId, botSellOrderId, "s1", 1L));
        tradeRepository.save(trade(NINE.plusMinutes(1), 2L, 1L, botBuyOrderId, botSellOrderId, "s1", 2L));
        flushAndClear();

        int deleted = tradeRepository.deleteBotOnlyTradesBefore(stock.getStockId(), NINE.plusMinutes(1));
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(tradeRepository.findAll()).extracting(Trade::getPrice).containsExactly(2L);
    }

    @Test
    void 이미_지워진_봇_주문만_가진_체결도_봇끼리의_체결로_보고_지운다() {
        tradeRepository.save(trade(NINE, 1L, 1L, 999_001L, 999_002L, "s1", 1L));
        flushAndClear();

        int deleted = tradeRepository.deleteBotOnlyTradesBefore(stock.getStockId(), NINE.plusMinutes(1));

        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void 다른_종목의_체결은_지우지_않는다() {
        Stock other = em.persist(new Stock("다른종목", Sector.FINANCE, 20_000L));
        tradeRepository.save(Trade.of(other, 5L, 1L, NINE.atZone(MarketTime.ZONE).toInstant(),
                botBuyOrderId, botSellOrderId, "s1", 1L));
        flushAndClear();

        int deleted = tradeRepository.deleteBotOnlyTradesBefore(stock.getStockId(), NINE.plusMinutes(1));

        assertThat(deleted).isZero();
    }

    private Trade trade(LocalDateTime executedAt, long price, long quantity, Long buyOrderId, Long sellOrderId,
                        String engineSessionId, Long engineTradeId) {
        Instant instant = executedAt.atZone(MarketTime.ZONE).toInstant();
        return Trade.of(stock, price, quantity, instant, buyOrderId, sellOrderId, engineSessionId, engineTradeId);
    }
}
