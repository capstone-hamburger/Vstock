package com.example.vstock.domain.userstock.entity;

import com.example.vstock.domain.stock.entity.Stock;
import com.example.vstock.domain.user.entity.User;
import com.example.vstock.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 보유 주식.
 */
@Getter
@Entity
@Table(
        name = "user_stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_stocks_user_id_stock_id", columnNames = {"user_id", "stock_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userStockId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private int quantity;

    // 평균 매입단가
    @Column(nullable = false)
    private Long price;

    public UserStock(User user, Stock stock, int quantity, Long price) {
        this.user = user;
        this.stock = stock;
        this.quantity = quantity;
        this.price = price;
    }
}
