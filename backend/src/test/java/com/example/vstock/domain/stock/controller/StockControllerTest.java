package com.example.vstock.domain.stock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Test
    void 현재가는_종목_ID로_조회하고_없는_종목은_404를_반환한다() throws Exception {
        Stock stock = new Stock("현재가테스트종목", Sector.IT, 10_000L);
        em.persist(stock);
        em.flush();

        mockMvc.perform(get("/stocks/{stockId}", stock.getStockId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockId").value(stock.getStockId()))
                .andExpect(jsonPath("$.name").value("현재가테스트종목"))
                .andExpect(jsonPath("$.currentPrice").value(10_000));
        mockMvc.perform(get("/stocks/{stockId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/stocks/{stockId}", 0))
                .andExpect(status().isBadRequest());
    }
}
