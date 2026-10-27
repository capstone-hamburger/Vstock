package com.example.vstock.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.vstock.domain.user.entity.User;
import com.example.vstock.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 회원을_저장하면_조회되고_createdAt이_자동_입력된다() {
        User saved = userRepository.save(new User("홍길동", "hong@test.com", "password", 1_000_000L, 0L));
        flushAndClear();

        User found = userRepository.findById(saved.getUserId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("홍길동");
        assertThat(found.getEmail()).isEqualTo("hong@test.com");
        assertThat(found.getBalance()).isEqualTo(1_000_000L);
        assertThat(found.getInvestment()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
