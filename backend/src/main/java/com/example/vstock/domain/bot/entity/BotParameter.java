package com.example.vstock.domain.bot.entity;

import com.example.vstock.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 2시간 구간별로 LLM에게 받은 봇 파라미터 이력.
 */
@Getter
@Entity
@Table(
        name = "bot_parameters",
        indexes = @Index(name = "idx_bot_parameters_bot_id_valid_from", columnList = "bot_id, valid_from")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotParameter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long botParameterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    // 체결 확률이 50%가 되는 가격
    @Column(nullable = false)
    private Long midPrice;

    // 확률이 1%p 변하는 데 필요한 가격 변동 (0보다 커야 함)
    @Column(nullable = false)
    private Long priceStep;

    // 분당 참여자 수 (0보다 커야 함)
    @Column(nullable = false)
    private double ratePerMinute;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validTo;

    public BotParameter(Bot bot, Long midPrice, Long priceStep, double ratePerMinute,
                        LocalDateTime validFrom, LocalDateTime validTo) {
        if (priceStep == null || priceStep <= 0) {
            throw new IllegalArgumentException("priceStep은 0보다 커야 합니다.");
        }
        if (ratePerMinute <= 0) {
            throw new IllegalArgumentException("ratePerMinute은 0보다 커야 합니다.");
        }
        this.bot = bot;
        this.midPrice = midPrice;
        this.priceStep = priceStep;
        this.ratePerMinute = ratePerMinute;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }
}
