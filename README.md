# Vstock

캡스톤 프로젝트 **Vstock** 모노레포. 백엔드와 프론트엔드를 한 레포에서 관리한다.

---

## 1. 레포 구조

```
Vstock/
├── backend/        # Spring Boot API 서버 (Java 21)
├── frontend/       # 웹 클라이언트
├── .github/        # 이슈 / PR 템플릿 (프론트·백 공용)
└── README.md       # 지금 이 문서 (공통 컨벤션)
```

| 폴더 | 설명 | 문서 |
| --- | --- | --- |
| `backend/` | Spring Boot + JPA + MySQL API 서버 | [backend/README.md](backend/README.md), [backend/CONTRIBUTING.md](backend/CONTRIBUTING.md) |
| `frontend/` | 웹 클라이언트 (스택 미정) | [frontend/README.md](frontend/README.md) |

각 폴더는 독립적으로 빌드/실행한다. 루트에는 빌드 도구를 두지 않는다.

---

## 2. 시작하기

```bash
git clone https://github.com/starvingorange/Vstock-backend.git
cd Vstock-backend
```

- 백엔드 실행: [backend/README.md](backend/README.md) 참고
- 프론트엔드 실행: [frontend/README.md](frontend/README.md) 참고

---

## 3. 브랜치 전략

- `main`: 배포/발표용 안정 버전. `develop`에서만 머지한다.
- `develop`: 개발 통합 브랜치. 모든 작업 브랜치는 `develop`에서 생성하고 `develop`으로 PR한다.

### 브랜치명

```
<타입>/<이슈번호>-<범위>-<영문 설명>
```

```
feat/12-backend-order-create-api
feat/13-frontend-order-create-modal
fix/15-backend-order-cancel-bug
chore/18-common-github-templates
```

- 타입은 커밋 타입과 동일하게 쓴다 (`feat`, `fix`, `refactor`, `docs`, `test`, `chore`).
- 브랜치명은 **영어 소문자 + 하이픈**으로 작성한다.

---

## 4. 커밋 컨벤션

모노레포이므로 **범위(scope)를 반드시 명시한다.**

```
<타입>(<범위>): <한국어 설명>
```

```
feat(backend): 주문 생성 API 구현
feat(frontend): 주문 생성 모달 구현
fix(backend): 주문 취소 시 체결 수량 계산 오류 수정
refactor(backend): OrderService에서 도메인 로직 분리
docs(common): README에 커밋 컨벤션 추가
test(backend): OrderServiceTest 케이스 추가
chore(frontend): ESLint 설정 추가
```

### 타입

| 타입 | 용도 |
| --- | --- |
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 |
| `chore` | 빌드/설정 등 기타 |

### 범위

| 범위 | 대상 |
| --- | --- |
| `backend` | `backend/` 하위 |
| `frontend` | `frontend/` 하위 |
| `common` | 루트 설정, `.github/`, 공통 문서 |

### 규칙

- 타입과 범위는 **영어 소문자**, 설명은 **한국어**로 작성한다.
- 제목 끝에 마침표를 쓰지 않는다.
- 하나의 커밋은 하나의 범위만 건드린다. `backend`와 `frontend`를 같이 수정해야 하면 커밋을 나누는 것을 원칙으로 하고, 불가피한 경우에만 `feat(backend,frontend):`로 적는다.
- 본문이 필요하면 제목 다음에 빈 줄을 두고 작성한다.

```
feat(backend): 주문 생성 API 구현

- 지정가/시장가 주문 분기 처리
- 잔고 부족 시 예외 처리 추가
```

---

## 5. 이슈 컨벤션

제목은 `[타입] 내용` 형식으로 **한국어**로 작성한다. 타입은 대문자로 쓴다.

```
[FEAT] 주문 생성 API 구현
[FIX] 주문 취소 시 체결 수량 계산 오류
[CHORE] 모노레포 구조 전환
```

작업 영역(백엔드/프론트엔드/공통)은 제목이 아니라 **이슈 템플릿의 체크박스와 라벨**로 표시한다.

템플릿: [버그 수정](.github/ISSUE_TEMPLATE/bug.md) · [기능 추가](.github/ISSUE_TEMPLATE/feature.md) · [기타 작업](.github/ISSUE_TEMPLATE/task.md)

---

## 6. PR 컨벤션

제목은 이슈와 동일하게 `[타입] 내용` 형식으로 작성한다.

```
[FEAT] 주문 생성 API 구현
[REFACTOR] OrderService에서 도메인 로직 분리
```

| 타입 | 용도 |
| --- | --- |
| `FEAT` | 새 기능 |
| `FIX` | 버그 수정 |
| `REFACTOR` | 리팩토링 (기능 변화 없음) |
| `DOCS` | 문서 수정 |
| `TEST` | 테스트 코드 |
| `CHORE` | 빌드/설정 등 기타 |

리뷰 규칙과 PR 크기 기준은 [CONTRIBUTING.md](CONTRIBUTING.md)에 정리되어 있다.

---

## 7. 코드 컨벤션

| 영역 | 문서 |
| --- | --- |
| 협업 프로세스 (이슈 → 브랜치 → PR → 머지) | [CONTRIBUTING.md](CONTRIBUTING.md) |
| 백엔드 (패키지 구조, 네이밍, DB, REST API) | [backend/CONTRIBUTING.md](backend/CONTRIBUTING.md) |
| 프론트엔드 | 스택 확정 후 `frontend/CONTRIBUTING.md`에 작성 |
