# Vstock-backend
캡스톤 백엔드 레포

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

GET    /users/{userId}/stocks
POST   /users/{userId}/orders
```

- 동사를 URL에 쓰지 않는다 (HTTP 메서드로 행위 표현).
- 경로 안에 여러 단어가 필요한 경우 kebab-case를 사용한다 (`/user-stocks` 형태가 필요한 예외적 상황 한정, 기본은 위처럼 중첩 리소스 경로로 표현 권장).

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

---

## 9. Git 브랜치 / 커밋 컨벤션

### 브랜치 전략

- `main`: 배포/발표용 안정 버전. `develop`에서만 머지한다.
- `develop`: 개발 통합 브랜치. 모든 작업 브랜치는 `develop`에서 생성하고 `develop`으로 PR한다.

### 브랜치명

```
feat/12-order-creation-api
fix/15-order-cancel-bug
refactor/ ...
docs/ ...
test/ ...
chore/ ...
```

형식: `feat/이슈번호-기능명-대상` (브랜치명은 영어로 기술)

### 커밋 메시지 (Conventional Commits)

타입은 영어 소문자로 쓰고, 내용은 **한국어**로 작성한다.

```
feat: 주문 생성 API 구현
fix: 주문 취소 시 체결 수량 계산 오류 수정
refactor: OrderService에서 도메인 로직 분리
docs: README에 배포 가이드 추가
test: OrderServiceTest 케이스 추가
chore: 의존성 버전 업데이트
```

| 타입 | 용도 |
| --- | --- |
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 |
| `chore` | 빌드/설정 등 기타 |

---

## 10. Issue 컨벤션

### Issue 제목

`[타입] 이슈 내용` 형식으로 **한국어**로 작성한다. 타입은 PR 컨벤션과 동일하게 사용한다.

```
[FEAT] 주문 생성 API 구현
[FIX] 주문 취소 시 체결 수량 계산 오류
```

---

## 11. Pull Request 컨벤션

### PR 제목

`[타입] 이슈 내용` 형식으로 작성하며, 내용은 **한국어**로 작성한다. 타입은 커밋 컨벤션과 동일하게 사용한다.

```
[FEAT] 주문 생성 API 구현
[FIX] 주문 취소 시 체결 수량 계산 오류 수정
[REFACTOR] OrderService에서 도메인 로직 분리
[DOCS] README에 배포 가이드 추가
[TEST] OrderServiceTest 케이스 추가
[CHORE] 의존성 버전 업데이트
```

| 타입 | 용도 |
| --- | --- |
| `FEAT` | 새 기능 |
| `FIX` | 버그 수정 |
| `REFACTOR` | 리팩토링 (기능 변화 없음) |
| `DOCS` | 문서 수정 |
| `TEST` | 테스트 코드 |
| `CHORE` | 빌드/설정 등 기타 |

### 리뷰 규칙

- PR은 팀원 최소 1인 이상의 승인(Approve) 후 머지한다.
- 리뷰어는 24시간 이내 확인을 원칙으로 한다 (급한 건은 별도 공지).
- 코드 스타일 지적보다 로직/설계 관련 코멘트를 우선한다.

### PR 크기

한 PR은 가능한 한 하나의 기능/수정 단위로 작게 유지한다. 여러 기능이 섞인 대형 PR은 이슈 단위로 쪼개서 올린다.