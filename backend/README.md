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
