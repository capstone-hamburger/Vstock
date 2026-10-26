package com.example.vstock.domain.userstock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.domain.userstock.entity.UserStock;
import com.example.vstock.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserStockRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private UserStockRepository userStockRepository;

    @Test
    void 보유주식을_저장하면_조회되고_createdAt이_자동_입력된다() {
        User user = em.persist(new User("홍길동", "hong@test.com", "password", 1_000_000L, 0L));
        Stock stock = em.persist(new Stock("테스트종목", Sector.IT, 10_000L));

        UserStock saved = userStockRepository.save(new UserStock(user, stock, 10, 9_500L));
        flushAndClear();

        UserStock found = userStockRepository.findById(saved.getUserStockId()).orElseThrow();

        assertThat(found.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(found.getStock().getStockId()).isEqualTo(stock.getStockId());
        assertThat(found.getQuantity()).isEqualTo(10);
        assertThat(found.getPrice()).isEqualTo(9_500L);
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
