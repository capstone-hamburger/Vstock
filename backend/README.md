# Vstock Backend

Vstock API 서버. Spring Boot + JPA + MySQL.

공통 컨벤션은 루트 [README.md](../README.md), 코드 컨벤션은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고한다.

---

## 기술 스택

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.2 |
| Gradle | 9.8.2 (wrapper) |
| MySQL | 8.0 (Docker) |

---

## 로컬 실행

아래 명령은 모두 **`backend/` 디렉터리에서** 실행한다. `docker-compose.yml`과 Spring의 `.env` 조회 경로가 모두 이 디렉터리를 기준으로 동작한다.

### 1. 환경변수 파일 생성

```bash
cd backend
cp .env.example .env
```

`.env`를 열어 값을 채운다.

```properties
MYSQL_PORT=3306
MYSQL_DATABASE=vstock
MYSQL_USER=vstock
MYSQL_PASSWORD=비밀번호
MYSQL_ROOT_PASSWORD=루트비밀번호

DB_URL=jdbc:mysql://localhost:3306/vstock?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=vstock
DB_PASSWORD=비밀번호
```

`.env`는 `.gitignore`에 등록되어 있다. 커밋하지 않는다.

### 2. MySQL 컨테이너 기동

```bash
docker compose up -d
```

중지는 `docker compose down`, 데이터까지 삭제하려면 `docker compose down -v`.

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

`local` 프로필이 기본 활성화되며, `LocalDataInitializer`가 시드 데이터를 넣는다.

### 4. 테스트

```bash
./gradlew test
```

대부분의 테스트는 위 MySQL 컨테이너가 떠 있어야 한다 (`@AutoConfigureTestDatabase(replace = NONE)`). 소켓 테스트만 예외로 DB 없이 돌아간다.

```bash
./gradlew test --tests "com.example.vstock.global.websocket.*"
```

---

## WebSocket

종목 상세 화면은 **진입 시 REST로 스냅샷을 받고, 이후 소켓으로 변경분만 받는다.**

| 항목 | 값 |
| --- | --- |
| 핸드셰이크 | `ws://localhost:8080/ws` (STOMP) |
| 구독 prefix | `/topic` |
| 발행 prefix | `/app` (클라이언트 → 서버) |
| 체결가 경로 | `/topic/stocks/{stockId}/price` |
| 호가 경로 | `/topic/stocks/{stockId}/orderbook` |
| 봉 경로 | `/topic/stocks/{stockId}/candles/{interval}` (`1m`, `10m`, `1h`, `1d`, `1M`) |
| 종목 스냅샷 | `GET /stocks/{stockId}` (진입 시 1회) |

종목 스냅샷 응답과 체결가 푸시 형식은 다음과 같다. 스냅샷의 `currentPrice`는 소켓과 같은 기준(최근 체결가, 체결이 없으면 DB 값)이고, 서버를 재시작한 직후에는 저장된 마지막 1분봉의 종가로 시작한다. 그래서 스냅샷을 받은 뒤 구독해도 값이 어긋나지 않는다.

```json
// GET /stocks/1
{ "stockId": 1, "name": "삼성전자", "sector": "IT", "currentPrice": 70300 }

// /topic/stocks/1/price (체결 1건마다)
{ "stockId": 1, "price": 70300, "quantity": 5, "executedAt": 1793059200000 }
```

`executedAt`은 epoch 밀리초다. 없는 종목은 404.

### 봉 푸시 규칙

봉(`/candles/{interval}`)은 체결 1건이 아니라 **그 봉의 전체 상태**를 보낸다. 클라이언트는 같은 `time`의 봉을 통째로 교체한다.

- **구독한 간격만 보낸다.** 그 경로를 구독하는 연결이 없으면 메시지를 만들지 않는다. 간격을 바꾸면 새 간격을 구독한 뒤 REST로 현재 봉부터 받는다. (구독자 수는 `SubscriptionRegistry`가 센다.)
- **진행 중인 봉(`finalized: false`)은 모아서 보낸다.** 경로별로 최신 상태만 들고 있다가 `vstock.websocket.candle-flush-millis`(기본 100ms)마다 한 번씩 보낸다. 봉은 교체하는 값이라 중간 상태를 건너뛰어도 결과가 같다.
- **마감된 봉(`finalized: true`)은 즉시 보낸다.** 모아 둔 이전 진행 상태는 버린다.
- 체결가(`/price`)는 체결마다 바로 보낸다.

### 구성

```
global/
├── config/
│   └── WebSocketConfig.java            # 엔드포인트·브로커·인터셉터·에러핸들러 등록
├── websocket/
│   ├── WebSocketDestination.java       # 구독·발행 경로 (문자열은 여기서만 만든다)
│   ├── StompAuthChannelInterceptor.java # CONNECT 인증 (#5 전까지 통과) + 클라이언트 SEND를 /app으로 제한
│   ├── StompEventListener.java         # 연결·구독 생명주기 로깅
│   ├── SubscriptionRegistry.java       # 경로별 구독자 수 (구독한 봉만 발행하기 위해)
│   └── WebSocketErrorHandler.java      # STOMP 예외 로깅 후 ERROR 프레임
└── security/                           # Spring Security 설정 (#5에서 구현)

domain/stock/
├── controller/
│   └── StockController.java            # GET /stocks/{stockId}
├── event/
│   └── TradeEvent.java                 # 체결 1건 (체결 엔진 연동부가 발행)
├── service/
│   ├── StockService.java               # 종목 스냅샷 (최근 체결가 우선)
│   ├── LatestPriceStore.java           # 종목별 최근 체결가 (메모리)
│   ├── LatestPriceInitializer.java     # 서버 시작 시 마지막 1분봉 종가로 최근 체결가 복구
│   ├── StockPriceListener.java         # TradeEvent → 현재가 갱신 + /price 푸시
│   └── StockMessagePublisher.java      # 종목 변경분 발행 (봉은 구독 간격만, 진행 봉은 모아서)
└── dto/                                # StockResponse, PriceResponse
```

소켓 **공통 설정은 `global/`**, **도메인별 발행은 `domain/<도메인>/service/`** 에 둔다. 발행할 때는 도메인 publisher를 주입받아 쓰고, 경로 문자열이나 `SimpMessagingTemplate`을 직접 다루지 않는다.

```java
stockMessagePublisher.publishPrice(stockId, payload);
stockMessagePublisher.publishOrderbook(stockId, payload);
```

> 클라이언트가 `/topic/...`로 직접 SEND하면 다른 구독자에게 가짜 시세를 뿌릴 수 있어서, 클라이언트의 SEND는 `/app/...`만 허용하고 나머지는 ERROR 프레임으로 거절한다. 서버가 발행하는 메시지에는 영향이 없다.
>
> `StompAuthChannelInterceptor`는 **아직 인증을 하지 않는다.** 로그인(#5)이 없어 토큰 검증 지점만 잡아둔 상태이고, 차트·호가창은 비로그인 구독 대상이라 현재는 통과시킨다. 인증이 필요한 구독을 추가하기 전에 반드시 채워야 한다.

### 허용 Origin

소켓 핸드셰이크는 CORS 대상이 아니라 Origin을 따로 허용해야 한다. `application.yml`의 값을 프론트 dev 서버 포트에 맞게 조정한다.

```yaml
vstock:
  websocket:
    allowed-origins: http://localhost:3000,http://localhost:5173
```

### 로컬 확인

앱을 띄운 뒤 브라우저 콘솔에서 확인한다 (허용 Origin에 포함된 페이지에서 실행해야 한다).

```js
const client = new StompJs.Client({ brokerURL: 'ws://localhost:8080/ws' });
client.onConnect = () => {
  client.subscribe('/topic/stocks/1/price', (msg) => console.log(JSON.parse(msg.body)));
};
client.activate();   // 연결

client.deactivate(); // 연결 끊기 (STOMP DISCONNECT 후 소켓 종료, 구독은 서버가 함께 정리한다)
```

종목 화면을 벗어날 때는 `deactivate()`를 호출한다. 탭이 닫히거나 네트워크가 끊겨 DISCONNECT를 못 보내는 경우를 위해 서버는 하트비트(10초)를 켜 두었고, 클라이언트가 하트비트를 협상했다면(`@stomp/stompjs`는 기본 10초) 그 시간 안에 신호가 없는 세션을 끊어서 정리한다.

구독자가 없을 때 발행된 메시지는 예외 없이 버려진다. 놓친 구간은 REST 스냅샷으로 다시 받는 것을 전제한다.

> 현재 브로커는 **인메모리**다. 서버를 여러 대로 늘리면 인스턴스별로 구독이 분리되므로 외부 브로커(Redis/RabbitMQ)로 교체해야 한다.
>
> `TradeEvent`를 실제로 발행하는 지점(체결 엔진 연동)은 후속 이슈 범위다. 그 전까지 `/price`는 이벤트를 직접 발행해야 움직인다.

---

## 체결 보관

엔진이 알려준 체결은 `trades`에 한 건씩 저장한다. 회원이 낀 체결은 기록이라 지우지 않고, **봇끼리의 체결은 서버 재시작 때 저장되지 않은 1분봉을 다시 만드는 재료**라서 `BotTradePurger`가 주기적으로 지운다.

- 종목별 삭제 기준은 **저장된 마지막 1분봉의 끝**과 **현재 시각 - 보관 시간** 중 이른 쪽이다. 서버가 꺼져 있던 동안의 체결과 저장에 실패한 봉의 재료가 지워지지 않게 하려는 것이다.
- 저장된 1분봉이 없는 종목은 지우지 않는다.

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `vstock.trade.bot-retention-minutes` | 5 | 봇 체결을 최소한 이만큼 남긴다 (1 이상) |
| `vstock.trade.purge-interval-millis` | 60000 | 정리 주기. 서버 시작 후 한 주기 뒤에 처음 실행한다 |

---

## 패키지 구조

```
com.example.vstock
├── global
│   ├── common      # BaseTimeEntity 등 공통 상위 타입
│   ├── config      # JPA·WebSocket 등 설정
│   ├── websocket   # 소켓 공통 (경로, 인터셉터, 이벤트, 에러 핸들러)
│   └── security    # Spring Security 설정 (#5)
├── domain          # 도메인별 entity / repository / service / controller / dto
│   ├── user
│   ├── bot
│   ├── stock
│   ├── order
│   └── userstock
└── infra           # 외부 시스템 연동 (C 체결 엔진, LLM API 등)
```

상세 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md) 참고.

---

## IntelliJ 설정

모노레포 전환으로 Gradle 프로젝트 루트가 `backend/`로 내려갔다. 기존에 레포 루트로 프로젝트를 열어 뒀다면 `backend/build.gradle`을 Gradle 프로젝트로 다시 import한다.

---

## 주문 API

이전한 주문 API는 회원 ID를 경로로 받는 프로토타입이다. 세션·JWT 인증은 아직 없으므로 회원 ID가 일치하는지 확인하는 것은 본인 인증을 의미하지 않는다. 최신 코드 컨벤션은 인증 주체에서 회원 ID를 가져오도록 요구한다. 로그인·인증 구현과 통합할 때 `@AuthenticationPrincipal`을 사용하고 주문 경로를 `POST /orders`, `DELETE /orders/{orderId}`로 전환해야 한다.

### 매수·매도 요청

`POST /users/{userId}/orders`

```json
{
  "stockId": 1,
  "type": "BUY",
  "quantity": 10,
  "price": 10000
}
```

- `type`은 `BUY`(매수) 또는 `SELL`(매도)이다. 현재는 지정가 주문만 지원한다.
- 종목 ID·수량·가격은 양의 정수이며 주문 금액은 `long` 범위 이내여야 한다.
- 성공하면 `201 Created`와 `PENDING` 주문을 반환한다. 주문 접수는 체결 완료를 의미하지 않는다.
- 매수는 모든 종목의 미체결 매수 예약금을 잔고에서 제외하고 검사한다.
- 매도는 해당 종목의 미체결 매도 예약 수량을 보유 수량에서 제외하고 검사한다.
- 같은 회원의 주문 생성·취소는 회원 행 잠금과 하나의 트랜잭션으로 처리한다.

### 매수·매도 취소

`DELETE /users/{userId}/orders/{orderId}`

- 성공하면 `200 OK`와 `CANCELED` 주문을 반환한다. 주문 행은 삭제하지 않는다.
- 경로의 회원이 소유한 대기 주문만 취소할 수 있다. 봇 주문은 이 API로 취소하지 않는다.
- 일부 체결된 주문은 기존 `filledQuantity`를 유지하고 미체결 잔량의 예약만 해제한다.
- 체결 완료·이미 취소된 주문은 `409 Conflict`를 반환한다.

주문 접수·취소는 실제 잔고와 보유 수량을 증감하지 않는다. 예약은 `PENDING` 주문의 `quantity - filledQuantity`로 계산한다. 추후 체결 처리는 같은 회원 잠금 순서(회원 → 주문)를 따르고, 실제 잔고·보유 수량·체결 수량을 함께 갱신해야 한다.

### 현재가 조회

`GET /stocks/{stockId}`는 [종목 스냅샷](#websocket)을 사용한다. 주문 접수·취소는 시세를 바꾸지 않는다.

### 주문 응답과 확인 방법

- 잘못된 입력: `400 Bad Request`
- 없는 회원·종목·주문 또는 경로의 회원이 소유하지 않은 주문: `404 Not Found`
- 잔고·보유 수량 부족 또는 취소 불가능한 상태: `409 Conflict`
- IntelliJ HTTP Client 예시는 `http/orders.http`에 있다. DB에 존재하는 회원·종목 ID와 응답으로 받은 주문 ID를 입력해 사용한다.
