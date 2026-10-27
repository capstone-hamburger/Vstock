package com.example.vstock.domain.bot.repository;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotParameter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotParameterRepository extends JpaRepository<BotParameter, Long> {

    // 봇별 가장 최근(validFrom 기준) 파라미터 조회
    Optional<BotParameter> findTopByBotOrderByValidFromDesc(Bot bot);
}
