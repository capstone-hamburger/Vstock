package com.example.vstock.global.config;

import com.example.vstock.global.common.MarketTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 봉 마감 스케줄러와 시간 기준. {@link Clock}을 주입해 쓰면 테스트에서 시간을 고정할 수 있다.
 */
@Configuration
@EnableScheduling
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(MarketTime.ZONE);
    }
}
