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

경로는 `WebSocketDestination`으로만 만들고 문자열을 직접 쓰지 않는다. 서버에서 발행할 때는 `WebSocketMessageSender`를 주입받아 쓴다.

```java
messageSender.send(WebSocketDestination.stockPrice(stockId), payload);
```

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
client.activate();
```

구독자가 없을 때 발행된 메시지는 예외 없이 버려진다. 놓친 구간은 REST 스냅샷으로 다시 받는 것을 전제한다.

> 현재 브로커는 **인메모리**다. 서버를 여러 대로 늘리면 인스턴스별로 구독이 분리되므로 외부 브로커(Redis/RabbitMQ)로 교체해야 한다.
>
> 차트·호가창 REST 조회 API와 실제 발행 지점(체결 엔진 연동)은 후속 이슈 범위다.

---

## 패키지 구조

```
com.example.vstock
├── global      # 공통 설정, 예외 처리, 유틸
├── domain      # 도메인별 entity / repository / service / controller / dto
│   ├── user
│   ├── bot
│   ├── stock
│   ├── order
│   └── userstock
└── infra       # 외부 시스템 연동 (C 체결 엔진, LLM API 등)
```

상세 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md) 참고.

---

## IntelliJ 설정

모노레포 전환으로 Gradle 프로젝트 루트가 `backend/`로 내려갔다. 기존에 레포 루트로 프로젝트를 열어 뒀다면 `backend/build.gradle`을 Gradle 프로젝트로 다시 import한다.
