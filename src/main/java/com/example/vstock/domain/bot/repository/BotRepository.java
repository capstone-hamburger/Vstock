package com.example.vstock.domain.bot.repository;

import com.example.vstock.domain.bot.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Long> {
}
