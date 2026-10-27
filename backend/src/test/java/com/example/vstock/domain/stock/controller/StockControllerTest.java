package com.example.vstock.domain.stock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.vstock.domain.stock.dto.CandleResponse;
import com.example.vstock.domain.stock.service.StockService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class StockControllerTest {

    private StockService stockService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StockController(stockService)).build();
    }

    @Test
    void interval을_생략하면_1분봉을_조회한다() throws Exception {
        when(stockService.findCandles(eq(1L), eq("1m"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new CandleResponse(1_793_059_200L, 100, 120, 90, 110, 7, false, 3)));

        mockMvc.perform(get("/stocks/1/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value(1_793_059_200L))
                .andExpect(jsonPath("$[0].open").value(100))
                .andExpect(jsonPath("$[0].finalized").value(false))
                .andExpect(jsonPath("$[0].seq").value(3));
    }

    @Test
    void ISO_시각과_limit을_서비스에_전달한다() throws Exception {
        mockMvc.perform(get("/stocks/1/candles")
                        .param("interval", "5m")
                        .param("from", "2026-10-27T09:00:00+09:00")
                        .param("to", "2026-10-27T10:00:00+09:00")
                        .param("limit", "50"))
                .andExpect(status().isOk());

        verify(stockService).findCandles(eq(1L), eq("5m"),
                eq(OffsetDateTime.parse("2026-10-27T09:00:00+09:00")),
                eq(OffsetDateTime.parse("2026-10-27T10:00:00+09:00")), eq(50));
    }

    @Test
    void 서비스가_400을_던지면_400으로_응답한다() throws Exception {
        when(stockService.findCandles(any(), eq("1y"), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 interval입니다."));

        mockMvc.perform(get("/stocks/1/candles").param("interval", "1y"))
                .andExpect(status().isBadRequest());
    }
}
