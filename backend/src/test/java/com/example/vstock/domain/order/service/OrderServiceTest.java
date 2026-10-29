package com.example.vstock.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.order.dto.CreateOrderRequest;
import com.example.vstock.domain.order.entity.OrderStatus;
import com.example.vstock.domain.order.entity.OrderType;
import com.example.vstock.domain.order.repository.OrderRepository;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.domain.userstock.entity.UserStock;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager em;

    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            for (Fixture fixture : fixtures) {
                em.createQuery("delete from Order o where o.user.userId = :id")
                        .setParameter("id", fixture.userId()).executeUpdate();
                em.createQuery("delete from UserStock h where h.user.userId = :id")
                        .setParameter("id", fixture.userId()).executeUpdate();
                em.remove(em.find(User.class, fixture.userId()));
                em.remove(em.find(Stock.class, fixture.stockId()));
            }
        });
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 같은_회원의_동시_주문은_잔고와_보유_수량을_초과해_예약하지_않는다(OrderType type) throws Exception {
        Fixture fixture = createFixture();
        Callable<Integer> request = () -> createOrder(fixture, type);

        assertThat(runTogether(request, request)).containsExactlyInAnyOrder(201, 409);

        transactions.executeWithoutResult(status -> {
            User user = em.find(User.class, fixture.userId());
            assertThat(orderRepository.findAllByUserAndTypeAndStatus(user, type, OrderStatus.PENDING))
                    .hasSize(1);
            assertThat(user.getBalance()).isEqualTo(100_000L);
            assertThat(em.createQuery("select h.quantity from UserStock h where h.user = :user", Integer.class)
                    .setParameter("user", user).getSingleResult()).isEqualTo(10);
        });
    }

    @Test
    void 서로_다른_회원은_첫_주문을_동시에_접수할_수_있다() throws Exception {
        Fixture first = createFixture();
        Fixture second = createFixture();

        assertThat(runTogether(() -> createOrder(first, OrderType.BUY),
                () -> createOrder(second, OrderType.BUY))).containsExactly(201, 201);
    }

    private Fixture createFixture() {
        Fixture fixture = transactions.execute(status -> {
            String id = UUID.randomUUID().toString();
            User user = new User("동시주문", id + "@test.com", "password", 100_000L, 0L);
            Stock stock = new Stock(id, Sector.IT, 10_000L);
            em.persist(user);
            em.persist(stock);
            em.persist(new UserStock(user, stock, 10, 9_000L));
            return new Fixture(user.getUserId(), stock.getStockId());
        });
        fixtures.add(fixture);
        return fixture;
    }

    private int createOrder(Fixture fixture, OrderType type) {
        try {
            orderService.createOrder(fixture.userId(),
                    new CreateOrderRequest(fixture.stockId(), type, 7, 10_000L));
            return 201;
        } catch (ResponseStatusException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return e.getStatusCode().value();
        }
    }

    private List<Integer> runTogether(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (Callable<Integer> request : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 주문 시작 시간이 초과됐습니다.");
                    }
                    return request.call();
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private record Fixture(Long userId, Long stockId) {
    }
}
