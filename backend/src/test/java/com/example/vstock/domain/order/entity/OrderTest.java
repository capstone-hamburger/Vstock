package com.example.vstock.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 주문 생성과 취소의 도메인 규칙은 DB 없이 검증한다.
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

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 주문_수량이_양수가_아니면_회원과_봇_주문을_거절한다(int quantity) {
        assertThatThrownBy(() -> Order.ofUser(user, stock, OrderType.BUY, quantity, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.ofBot(bot, stock, OrderType.SELL, quantity, 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 주문_가격이_양수가_아니면_거절한다(long price) {
        assertThatThrownBy(() -> Order.ofUser(user, stock, OrderType.BUY, 1, price))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문_금액이_long_범위를_넘으면_거절한다() {
        assertThatThrownBy(() -> Order.ofUser(user, stock, OrderType.BUY, 2, Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 일부_체결된_주문을_취소해도_체결_수량은_유지된다() {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 10, 10_000L);
        ReflectionTestUtils.setField(order, "filledQuantity", 3);

        order.updateStatusToCanceled();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getQuantity()).isEqualTo(10);
        assertThat(order.getFilledQuantity()).isEqualTo(3);
    }

    @Test
    void 이미_취소된_주문은_다시_취소할_수_없다() {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 1, 10_000L);
        order.updateStatusToCanceled();

        assertThatThrownBy(order::updateStatusToCanceled).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 전량_체결된_주문은_취소할_수_없다() {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 1, 10_000L);
        ReflectionTestUtils.setField(order, "filledQuantity", 1);

        assertThatThrownBy(order::updateStatusToCanceled).isInstanceOf(IllegalStateException.class);
    }
}
