package com.example.vstock.support;

import com.example.vstock.global.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 로컬 Docker MySQL을 그대로 쓰는 Repository 테스트 공통 설정.
 * 개발 DB의 시드 데이터(봇 6개 등)와 unique 제약이 충돌하지 않도록 테스트 트랜잭션 안에서 테이블을 비운다.
 * 테스트가 끝나면 롤백되므로 기존 데이터는 그대로 남는다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositoryTestSupport {

    // FK 참조 순서대로 삭제
    private static final String[] TABLES = {
            "orders", "user_stocks", "bot_parameters", "bots", "stocks", "users"
    };

    @Autowired
    protected TestEntityManager em;

    @BeforeEach
    void clearTables() {
        for (String table : TABLES) {
            em.getEntityManager().createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
    }

    // 저장 후 영속성 컨텍스트를 비워서 이후 조회가 DB에서 일어나게 한다
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
