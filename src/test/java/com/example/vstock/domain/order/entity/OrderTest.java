package com.example.vstock.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import org.junit.jupiter.api.Test;

/**
 * 정적 팩토리의 null 검증은 순수 Java 로직이므로 DB 없이 검증한다.
 */
class OrderTest {

    private final User user = new User("홍길동", "hong@test.com", "password", 1_000_000L, 0L);
    private final Bot bot = new Bot("AGGRESSIVE_BUYER", Persona.AGGRESSIVE, BotSide.BUYER);
    private final Stock stock = new Stock("테스트종목", Sector.IT, 10_000L);

    @Test
    void ofUser에_user가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofUser(null, stock, OrderType.BUY, 1, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofUser에_stock이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofUser(user, null, OrderType.BUY, 1, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofUser에_type이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofUser(user, stock, null, 1, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofUser에_price가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofUser(user, stock, OrderType.BUY, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofBot에_bot이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofBot(null, stock, OrderType.SELL, 1, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofBot에_stock이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> Order.ofBot(bot, null, OrderType.SELL, 1, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
