package com.example.vstock.domain.bot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BotRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private BotRepository botRepository;

    @Test
    void 봇을_저장하면_조회되고_createdAt이_자동_입력된다() {
        Bot saved = botRepository.save(new Bot("AGGRESSIVE_BUYER", Persona.AGGRESSIVE, BotSide.BUYER));
        flushAndClear();

        Bot found = botRepository.findById(saved.getBotId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("AGGRESSIVE_BUYER");
        assertThat(found.getPersona()).isEqualTo(Persona.AGGRESSIVE);
        assertThat(found.getSide()).isEqualTo(BotSide.BUYER);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
