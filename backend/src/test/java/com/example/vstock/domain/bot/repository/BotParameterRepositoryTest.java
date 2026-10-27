package com.example.vstock.domain.bot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotParameter;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.support.RepositoryTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BotParameterRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime MARKET_OPEN = LocalDateTime.of(2026, 10, 26, 9, 0);

    @Autowired
    private BotParameterRepository botParameterRepository;

    @Test
    void 봇_파라미터를_저장하면_조회되고_createdAt이_자동_입력된다() {
        Bot bot = em.persist(new Bot("AGGRESSIVE_BUYER", Persona.AGGRESSIVE, BotSide.BUYER));

        BotParameter saved = botParameterRepository.save(
                new BotParameter(bot, 10_000L, 50L, 12.5, MARKET_OPEN, MARKET_OPEN.plusHours(2)));
        flushAndClear();

        BotParameter found = botParameterRepository.findById(saved.getBotParameterId()).orElseThrow();

        assertThat(found.getBot().getBotId()).isEqualTo(bot.getBotId());
        assertThat(found.getMidPrice()).isEqualTo(10_000L);
        assertThat(found.getPriceStep()).isEqualTo(50L);
        assertThat(found.getRatePerMinute()).isEqualTo(12.5);
        assertThat(found.getValidFrom()).isEqualTo(MARKET_OPEN);
        assertThat(found.getValidTo()).isEqualTo(MARKET_OPEN.plusHours(2));
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 봇별로_validFrom이_가장_늦은_파라미터를_조회한다() {
        Bot buyer = em.persist(new Bot("AGGRESSIVE_BUYER", Persona.AGGRESSIVE, BotSide.BUYER));
        Bot seller = em.persist(new Bot("AGGRESSIVE_SELLER", Persona.AGGRESSIVE, BotSide.SELLER));

        // 저장 순서와 최신 순서가 다르도록 늦은 구간을 먼저 저장한다
        em.persist(new BotParameter(buyer, 10_200L, 50L, 10.0, MARKET_OPEN.plusHours(4), MARKET_OPEN.plusHours(6)));
        em.persist(new BotParameter(buyer, 10_000L, 50L, 10.0, MARKET_OPEN, MARKET_OPEN.plusHours(2)));
        em.persist(new BotParameter(buyer, 10_100L, 50L, 10.0, MARKET_OPEN.plusHours(2), MARKET_OPEN.plusHours(4)));
        // 다른 봇의 더 늦은 파라미터는 결과에 섞이면 안 된다
        em.persist(new BotParameter(seller, 9_000L, 30L, 5.0, MARKET_OPEN.plusHours(6), MARKET_OPEN.plusHours(8)));
        flushAndClear();

        BotParameter latest = botParameterRepository.findTopByBotOrderByValidFromDesc(buyer).orElseThrow();

        assertThat(latest.getBot().getBotId()).isEqualTo(buyer.getBotId());
        assertThat(latest.getValidFrom()).isEqualTo(MARKET_OPEN.plusHours(4));
        assertThat(latest.getMidPrice()).isEqualTo(10_200L);
    }

    @Test
    void 파라미터가_없는_봇은_빈_결과를_반환한다() {
        Bot bot = em.persist(new Bot("CONSERVATIVE_BUYER", Persona.CONSERVATIVE, BotSide.BUYER));
        flushAndClear();

        assertThat(botParameterRepository.findTopByBotOrderByValidFromDesc(bot)).isEmpty();
    }
}
