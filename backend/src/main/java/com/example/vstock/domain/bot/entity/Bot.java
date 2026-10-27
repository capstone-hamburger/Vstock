package com.example.vstock.domain.bot.entity;

import com.example.vstock.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 투자자 봇. 무제한 자금으로 가정하므로 잔고/보유주식은 추적하지 않는다.
 */
@Getter
@Entity
@Table(
        name = "bots",
        uniqueConstraints = @UniqueConstraint(name = "uk_bots_persona_side", columnNames = {"persona", "side"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long botId;

    @Column(nullable = false, unique = true)
    private String name;

    // columnDefinition으로 타입을 직접 지정해 MySQL 네이티브 ENUM과 enum 값 CHECK 제약을 모두 피한다
    // (ddl-auto=update는 enum 값이 추가돼도 둘 다 갱신하지 않는다)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private BotSide side;

    @Column(nullable = false)
    private boolean active = true;

    public Bot(String name, Persona persona, BotSide side) {
        this.name = name;
        this.persona = persona;
        this.side = side;
    }
}
