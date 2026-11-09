package com.example.vstock.domain.trade.repository;

import com.example.vstock.domain.trade.entity.Trade;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** 같은 엔진 체결을 이미 저장했는지. */
    boolean existsByEngineSessionIdAndEngineTradeId(String engineSessionId, Long engineTradeId);

    /**
     * 체결 시각이 {@code from} 이후인 체결을 오래된 순으로 조회한다. 서버 시작 시 저장되지 않은
     * 진행 중인 봉을 다시 만들 때 쓴다. 같은 시각이면 저장된 순서를 따른다.
     */
    @Query("""
            select t from Trade t
            where t.stock.stockId = :stockId and t.executedAt >= :from
            order by t.executedAt asc, t.tradeId asc
            """)
    List<Trade> findTradesSince(@Param("stockId") Long stockId, @Param("from") LocalDateTime from);

    /**
     * 체결 시각이 {@code before} 이전인, 양쪽 주문이 모두 봇인 체결을 지운다. 회원이 낀 체결은 지우지 않는다.
     * 주문이 이미 지워졌어도 회원 주문이 아니면 봇끼리의 체결로 본다(회원 주문은 지우지 않으므로).
     *
     * @return 지운 행 수
     */
    @Transactional
    @Modifying
    @Query("""
            delete from Trade t
            where t.stock.stockId = :stockId and t.executedAt < :before
              and not exists (
                select 1 from Order o
                where o.traderType = com.example.vstock.domain.order.entity.TraderType.USER
                  and (o.orderId = t.buyOrderId or o.orderId = t.sellOrderId))
            """)
    int deleteBotOnlyTradesBefore(@Param("stockId") Long stockId, @Param("before") LocalDateTime before);
}
