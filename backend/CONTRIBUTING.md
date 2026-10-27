# 백엔드 코드 컨벤션

Vstock 백엔드(`backend/`)의 코드 작성 규칙.

브랜치/커밋/이슈/PR 형식은 루트 [README.md](../README.md), 협업 프로세스는 루트 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

---

## 1. 패키지 구조

도메인 기반 패키지 구조를 따른다 (계층별 최상위 분리 지양).

```
com.example.vstock
├── global      # 공통 설정, 예외 처리, 유틸
├── domain
│   ├── user
│   ├── bot
│   ├── stock
│   ├── order
│   └── ...
└── infra       # 외부 시스템 연동 (C 체결 엔진, LLM API 등)
```

- 각 도메인 폴더 안에 `controller / service / repository / entity / dto`를 둔다.

---

## 2. 클래스 네이밍

| 계층 | 규칙 | 예시 |
| --- | --- | --- |
| Entity | 도메인 명사, 접미사 없음 | `Order`, `User`, `Stock` |
| Controller | `{도메인}Controller` | `OrderController` |
| Service | `{도메인}Service` | `OrderService` |
| Repository | `{도메인}Repository` | `OrderRepository` |
| DTO | 용도별 접미사 구분 (Request/Response) | `CreateOrderRequest`, `OrderResponse` |

### DTO 세부 규칙

- 생성: `Create{대상}Request`
- 수정: `Update{대상}Request`
- 응답: `{대상}Response`
- 목록 응답 등 변형이 필요하면 접두사로 구분: `Find{대상}ListResponse`

---

## 3. 메서드 네이밍

### Service / Repository CRUD 동사 통일

| 동작 | 동사 |
| --- | --- |
| 생성 | `create` |
| 조회 | `find` |
| 수정 | `update` |
| 삭제 | `delete` |

```java
Order createOrder(CreateOrderRequest request);
Order findOrderById(Long orderId);
Order updateOrder(Long orderId, UpdateOrderRequest request);
void deleteOrder(Long orderId);
```

`get`, `save`, `remove` 등 다른 동사와 혼용하지 않는다.

---

## 4. DB 네이밍 (MySQL)

테이블명/컬럼명은 **snake_case**를 사용한다.

```sql
CREATE TABLE user_stocks (
    user_stock_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    stock_id BIGINT,
    created_at DATETIME
);
```

- 테이블명은 복수형으로 통일한다 (`users`, `stocks`, `user_stocks`).
- Java 필드는 camelCase로 두고, JPA가 자동으로 snake_case로 매핑하도록 한다 (`@Column` 명시하지 않아도 Hibernate 기본 전략이 처리).

---

## 5. REST API 엔드포인트 네이밍

리소스는 **복수형 명사**로 통일한다.

```
GET    /orders
GET    /orders/{orderId}
POST   /orders
PATCH  /orders/{orderId}
DELETE /orders/{orderId}
```

- 동사를 URL에 쓰지 않는다 (HTTP 메서드로 행위 표현).
- 경로 안에 여러 단어가 필요한 경우 kebab-case를 사용한다 (`/user-stocks` 형태가 필요한 예외적 상황 한정, 기본은 중첩 리소스 경로로 표현 권장).

### 본인 리소스는 경로에 userId를 넣지 않는다

로그인한 사용자 **본인**의 데이터를 다루는 API는 `userId`를 경로나 요청 바디로 받지 않는다. `@AuthenticationPrincipal`로 인증 주체를 꺼내 그 값으로 조회한다.

```
GET    /users/me              # 내 정보 조회
PATCH  /users/me              # 내 정보 수정
GET    /users/me/stocks       # 내 보유 주식
GET    /users/me/orders       # 내 주문 내역
POST   /orders                # 주문 생성 (주문자는 인증 정보에서 결정)
```

```java
@GetMapping("/users/me")
public UserResponse findMyInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return userService.findUserById(userDetails.getUserId());
}
```

- 클라이언트가 보낸 `userId`를 신뢰하면 남의 정보를 조회·수정할 수 있다 (권한 상승). 식별자는 **항상 서버가 인증 정보에서 가져온다.**
- Controller에서만 `@AuthenticationPrincipal`을 받고, Service에는 `Long userId`만 넘긴다. Service가 `SecurityContext`를 직접 참조하지 않게 한다 (테스트 용이성).
- `/users/{userId}`처럼 경로에 식별자를 두는 API는 **관리자용 또는 다른 사용자의 공개 정보 조회**에만 사용하고, 반드시 권한 검증을 둔다.

`CustomUserDetails`(인증 주체 타입)는 로그인/회원가입 작업에서 정의한다.

---

## 6. Enum 네이밍

Enum 상수는 UPPER_SNAKE_CASE, 필요 시 한글 설명을 별도 필드로 가진다.

```java
public enum Sector {
    FINANCE("금융"),
    IT("IT"),
    MEDICAL("의료");

    private final String description;

    Sector(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

JPA 저장 시 반드시 `@Enumerated(EnumType.STRING)`을 명시한다 (`ORDINAL` 금지).
DB 컬럼은 MySQL 네이티브 `ENUM`이 아닌 `VARCHAR`로 생성되게 한다 (enum 값 추가 시 `ddl-auto=update`가 컬럼을 변경하지 않기 때문).

---

## 7. 상수 (static final) 네이밍

UPPER_SNAKE_CASE를 사용한다.

```java
public static final int MAX_RETRY_COUNT = 5;
private static final long INITIAL_STOCK_PRICE = 10_000L;
```

---

## 8. 테스트 네이밍

테스트 클래스는 대상 클래스명 + `Test` 접미사를 사용한다.

```java
class OrderServiceTest { ... }
class BotParameterRepositoryTest { ... }
```

테스트 메서드명은 한글로 상황을 명확히 기술하는 것을 허용한다 (팀 내 가독성 우선).

```java
@Test
void 주문_생성_시_초기_상태는_대기중이다() { ... }
```
