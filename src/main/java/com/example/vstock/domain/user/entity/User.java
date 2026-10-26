package com.example.vstock.domain.user.entity;

import com.example.vstock.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사람 회원. 봇은 별도 엔티티(Bot)로 관리한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // 해시 저장 예정
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Long balance;

    @Column(nullable = false)
    private Long investment;

    public User(String name, String email, String password, Long balance, Long investment) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.balance = balance;
        this.investment = investment;
    }
}
