package com.example.vstock.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.order.entity.TraderType;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.support.RepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private OrderRepository orderRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = em.persist(new Stock("테스트종목", Sector.IT, 10_000L));
    }

    @Test
    void 회원_주문을_ofUser로_생성하면_저장된다() {
        User user = em.persist(new User("홍길동", "hong@test.com", "password", 1_000_000L, 0L));

        Order saved = orderRepository.save(Order.ofUser(user, stock, OrderType.BUY, 5, 10_100L));
        flushAndClear();

        Order found = orderRepository.findById(saved.getOrderId()).orElseThrow();

        assertThat(found.getTraderType()).isEqualTo(TraderType.USER);
        assertThat(found.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(found.getBot()).isNull();
        assertThat(found.getStock().getStockId()).isEqualTo(stock.getStockId());
        assertThat(found.getType()).isEqualTo(OrderType.BUY);
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getQuantity()).isEqualTo(5);
        assertThat(found.getFilledQuantity()).isZero();
        assertThat(found.getPrice()).isEqualTo(10_100L);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 봇_주문을_ofBot으로_생성하면_저장된다() {
        Bot bot = em.persist(new Bot("MOMENTUM_SELLER", Persona.MOMENTUM, BotSide.SELLER));

        Order saved = orderRepository.save(Order.ofBot(bot, stock, OrderType.SELL, 3, 9_900L));
        flushAndClear();

        Order found = orderRepository.findById(saved.getOrderId()).orElseThrow();

        assertThat(found.getTraderType()).isEqualTo(TraderType.BOT);
        assertThat(found.getBot().getBotId()).isEqualTo(bot.getBotId());
        assertThat(found.getUser()).isNull();
        assertThat(found.getType()).isEqualTo(OrderType.SELL);
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getFilledQuantity()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
