package com.example.vstock.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StockRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private StockRepository stockRepository;

    @Test
    void 종목을_저장하면_조회되고_createdAt이_자동_입력된다() {
        Stock saved = stockRepository.save(new Stock("테스트종목", Sector.FINANCE, 10_000L));
        flushAndClear();

        Stock found = stockRepository.findById(saved.getStockId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("테스트종목");
        assertThat(found.getSector()).isEqualTo(Sector.FINANCE);
        assertThat(found.getCurrentPrice()).isEqualTo(10_000L);
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
