package com.example.vstock.global.config;

import com.example.vstock.domain.bot.entity.Bot;
import com.example.vstock.domain.bot.entity.BotSide;
import com.example.vstock.domain.bot.entity.Persona;
import com.example.vstock.domain.bot.repository.BotRepository;
import com.example.vstock.domain.stock.entity.Sector;
import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.stock.repository.StockRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로필 전용 시드 데이터. 테이블이 비어 있을 때만 삽입한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    // 임시값
    private static final String SEED_STOCK_NAME = "브이스톡";
    private static final Sector SEED_STOCK_SECTOR = Sector.IT;
    private static final long INITIAL_STOCK_PRICE = 10_000L;

    private final StockRepository stockRepository;
    private final BotRepository botRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedStock();
        seedBots();
    }

    private void seedStock() {
        if (stockRepository.count() > 0) {
            log.info("시드 데이터: 종목이 이미 있어 건너뜁니다.");
            return;
        }
        stockRepository.save(new Stock(SEED_STOCK_NAME, SEED_STOCK_SECTOR, INITIAL_STOCK_PRICE));
        log.info("시드 데이터: 종목 1개를 삽입했습니다.");
    }

    private void seedBots() {
        if (botRepository.count() > 0) {
            log.info("시드 데이터: 봇이 이미 있어 건너뜁니다.");
            return;
        }
        List<Bot> bots = new ArrayList<>();
        for (Persona persona : Persona.values()) {
            for (BotSide side : BotSide.values()) {
                bots.add(new Bot(persona.name() + "_" + side.name(), persona, side));
            }
        }
        botRepository.saveAll(bots);
        log.info("시드 데이터: 봇 {}개를 삽입했습니다.", bots.size());
    }
}
