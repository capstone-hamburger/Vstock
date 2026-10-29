package com.example.vstock.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.order.dto.CreateOrderRequest;
import com.example.vstock.domain.order.dto.OrderResponse;
import com.example.vstock.domain.order.entity.Order;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.order.repository.OrderRepository;
import com.example.vstock.domain.order.service.OrderService;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.domain.userstock.entity.UserStock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private User user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = new User("주문테스트", "order-api@test.com", "password", 100_000L, 0L);
        stock = new Stock("주문테스트종목", Sector.IT, 10_000L);
        em.persist(user);
        em.persist(stock);
        em.persist(new UserStock(user, stock, 10, 9_000L));
        em.flush();
    }

    @Test
    void 매수_요청은_대기_주문을_생성하고_잔고와_현재가를_바꾸지_않는다() throws Exception {
        mockMvc.perform(createRequest("\"type\":\"BUY\",\"quantity\":5,\"price\":11000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.stockId").value(stock.getStockId()))
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.filledQuantity").value(0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());

        em.flush();
        em.clear();
        assertThat(em.find(User.class, user.getUserId()).getBalance()).isEqualTo(100_000L);
        assertThat(em.find(Stock.class, stock.getStockId()).getCurrentPrice()).isEqualTo(10_000L);
    }

    @Test
    void 매도_요청은_보유_수량을_즉시_차감하지_않는다() throws Exception {
        mockMvc.perform(createRequest("\"type\":\"SELL\",\"quantity\":7,\"price\":10000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SELL"));

        em.flush();
        em.clear();
        assertThat(em.createQuery("select h.quantity from UserStock h where h.user.userId = :id", Integer.class)
                .setParameter("id", user.getUserId()).getSingleResult()).isEqualTo(10);
    }

    @Test
    void 매수_예약은_다른_종목의_미체결_주문까지_합산한다() throws Exception {
        createOrder(OrderType.BUY, 7);
        Stock other = new Stock("다른종목", Sector.FINANCE, 10_000L);
        em.persist(other);

        mockMvc.perform(post("/users/{userId}/orders", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":%d,\"type\":\"BUY\",\"quantity\":4,\"price\":10000}"
                                .formatted(other.getStockId())))
                .andExpect(status().isConflict());
    }

    @Test
    void 매수_취소_후에는_예약_금액만큼_다시_주문할_수_있다() throws Exception {
        OrderResponse order = createOrder(OrderType.BUY, 10);
        mockMvc.perform(createRequest("\"type\":\"BUY\",\"quantity\":1,\"price\":10000"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mockMvc.perform(createRequest("\"type\":\"BUY\",\"quantity\":10,\"price\":10000"))
                .andExpect(status().isCreated());
        assertThat(user.getBalance()).isEqualTo(100_000L);
    }

    @Test
    void 매도_취소_후에는_예약_수량만큼_다시_주문할_수_있다() throws Exception {
        OrderResponse order = createOrder(OrderType.SELL, 10);
        mockMvc.perform(createRequest("\"type\":\"SELL\",\"quantity\":1,\"price\":10000"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mockMvc.perform(createRequest("\"type\":\"SELL\",\"quantity\":10,\"price\":10000"))
                .andExpect(status().isCreated());
    }

    @Test
    void 보유하지_않은_종목은_매도할_수_없다() throws Exception {
        Stock other = new Stock("미보유종목", Sector.MEDICAL, 10_000L);
        em.persist(other);

        mockMvc.perform(post("/users/{userId}/orders", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":%d,\"type\":\"SELL\",\"quantity\":1,\"price\":10000}"
                                .formatted(other.getStockId())))
                .andExpect(status().isConflict());
    }

    @Test
    void 부분_체결된_매수는_남은_금액만_예약한다() throws Exception {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 10, 10_000L);
        ReflectionTestUtils.setField(order, "filledQuantity", 4);
        em.persist(order);

        mockMvc.perform(createRequest("\"type\":\"BUY\",\"quantity\":4,\"price\":10000"))
                .andExpect(status().isCreated());
        mockMvc.perform(createRequest("\"type\":\"BUY\",\"quantity\":1,\"price\":10000"))
                .andExpect(status().isConflict());
    }

    @Test
    void 부분_체결된_매도는_남은_수량만_예약한다() throws Exception {
        Order order = Order.ofUser(user, stock, OrderType.SELL, 10, 10_000L);
        ReflectionTestUtils.setField(order, "filledQuantity", 4);
        em.persist(order);

        mockMvc.perform(createRequest("\"type\":\"SELL\",\"quantity\":4,\"price\":10000"))
                .andExpect(status().isCreated());
        mockMvc.perform(createRequest("\"type\":\"SELL\",\"quantity\":1,\"price\":10000"))
                .andExpect(status().isConflict());
    }

    @Test
    void 일부_체결된_주문을_취소해도_체결_내역은_유지된다() throws Exception {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 10, 10_000L);
        ReflectionTestUtils.setField(order, "filledQuantity", 3);
        em.persist(order);

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.filledQuantity").value(3));

        em.flush();
        em.clear();
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getFilledQuantity()).isEqualTo(3);
    }

    @Test
    void 다른_회원의_주문은_취소할_수_없다() throws Exception {
        OrderResponse order = createOrder(OrderType.BUY, 1);
        User other = new User("다른회원", "other-order@test.com", "password", 100_000L, 0L);
        em.persist(other);

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", other.getUserId(), order.getOrderId()))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void 봇_주문은_회원_취소_API로_취소할_수_없다() throws Exception {
        Bot bot = em.createQuery("select b from Bot b", Bot.class).setMaxResults(1).getSingleResult();
        Order order = Order.ofBot(bot, stock, OrderType.SELL, 1, 10_000L);
        em.persist(order);

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), order.getOrderId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 체결_완료나_취소된_주문은_취소할_수_없다() throws Exception {
        Order order = Order.ofUser(user, stock, OrderType.BUY, 1, 10_000L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETE);
        ReflectionTestUtils.setField(order, "filledQuantity", 1);
        em.persist(order);

        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), order.getOrderId()))
                .andExpect(status().isConflict());

        OrderResponse pending = createOrder(OrderType.BUY, 1);
        orderService.deleteOrder(user.getUserId(), pending.getOrderId());
        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), pending.getOrderId()))
                .andExpect(status().isConflict());
    }

    @Test
    void 존재하지_않는_회원과_종목과_주문은_404를_반환한다() throws Exception {
        mockMvc.perform(post("/users/{userId}/orders", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":%d,\"type\":\"BUY\",\"quantity\":1,\"price\":10000}"
                                .formatted(stock.getStockId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/users/{userId}/orders", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":9223372036854775807,\"type\":\"BUY\",\"quantity\":1,\"price\":10000}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/users/{userId}/orders/{orderId}", user.getUserId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"type\":null,\"quantity\":1,\"price\":10000",
            "\"type\":\"INVALID\",\"quantity\":1,\"price\":10000",
            "\"type\":0,\"quantity\":1,\"price\":10000",
            "\"type\":\"0\",\"quantity\":1,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":null,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":0,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":-1,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":1.5,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":2147483648,\"price\":10000",
            "\"type\":\"BUY\",\"quantity\":1,\"price\":null",
            "\"type\":\"BUY\",\"quantity\":1,\"price\":0",
            "\"type\":\"BUY\",\"quantity\":1,\"price\":-1",
            "\"type\":\"BUY\",\"quantity\":1,\"price\":10000.5",
            "\"type\":\"BUY\",\"quantity\":2,\"price\":9223372036854775807"
    })
    void 잘못된_주문은_400을_반환하고_저장하지_않는다(String fields) throws Exception {
        mockMvc.perform(createRequest(fields)).andExpect(status().isBadRequest());
        assertThat(orderRepository.findAllByUserAndTypeAndStatus(user, OrderType.BUY, OrderStatus.PENDING))
                .isEmpty();
    }

    @Test
    void 필수_종목과_본문이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/users/{userId}/orders", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BUY\",\"quantity\":1,\"price\":10000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/users/{userId}/orders", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private OrderResponse createOrder(OrderType type, int quantity) {
        return orderService.createOrder(user.getUserId(),
                new CreateOrderRequest(stock.getStockId(), type, quantity, 10_000L));
    }

    private MockHttpServletRequestBuilder createRequest(String fields) {
        return post("/users/{userId}/orders", user.getUserId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockId\":" + stock.getStockId() + "," + fields + "}");
    }
}
