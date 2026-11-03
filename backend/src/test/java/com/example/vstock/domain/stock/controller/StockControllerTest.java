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
import com.example.vstock.domain.stock.dto.FindCandleListResponse;
import com.example.vstock.domain.stock.dto.StockResponse;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.service.StockService;
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
                .thenReturn(new FindCandleListResponse("1m",
                        List.of(new CandleResponse(1_793_059_200L, 100, 120, 90, 110, 7, false, 3)), 1_793_059_140L));

        mockMvc.perform(get("/stocks/1/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1m"))
                .andExpect(jsonPath("$.candles[0].time").value(1_793_059_200L))
                .andExpect(jsonPath("$.candles[0].open").value(100))
                .andExpect(jsonPath("$.candles[0].finalized").value(false))
                .andExpect(jsonPath("$.candles[0].seq").value(3))
                .andExpect(jsonPath("$.nextBefore").value(1_793_059_140L));
    }

    @Test
    void 더_과거의_봉이_없으면_nextBefore는_null이다() throws Exception {
        when(stockService.findCandles(any(), any(), any(), any(), any()))
                .thenReturn(new FindCandleListResponse("1h", List.of(), null));

        mockMvc.perform(get("/stocks/1/candles").param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextBefore").value((Object) null));
    }

    @Test
    void 파라미터를_서비스에_전달한다() throws Exception {
        when(stockService.findCandles(any(), any(), any(), any(), any()))
                .thenReturn(new FindCandleListResponse("1M", List.of(), null));

        mockMvc.perform(get("/stocks/1/candles")
                        .param("interval", "1M")
                        .param("from", "1000")
                        .param("before", "2000")
                        .param("limit", "50"))
                .andExpect(status().isOk());

        verify(stockService).findCandles(1L, "1M", 1000L, 2000L, 50);
    }

    @Test
    void 서비스가_400을_던지면_400으로_응답한다() throws Exception {
        when(stockService.findCandles(any(), eq("1y"), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 interval입니다."));

        mockMvc.perform(get("/stocks/1/candles").param("interval", "1y"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 종목_정보와_현재가를_조회한다() throws Exception {
        when(stockService.findStockById(1L))
                .thenReturn(new StockResponse(1L, "테스트종목", Sector.FINANCE, 10_700L));

        mockMvc.perform(get("/stocks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockId").value(1))
                .andExpect(jsonPath("$.name").value("테스트종목"))
                .andExpect(jsonPath("$.sector").value("FINANCE"))
                .andExpect(jsonPath("$.currentPrice").value(10_700));
    }

    @Test
    void 존재하지_않는_종목이면_404로_응답한다() throws Exception {
        when(stockService.findStockById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다."));

        mockMvc.perform(get("/stocks/99")).andExpect(status().isNotFound());
    }
}
