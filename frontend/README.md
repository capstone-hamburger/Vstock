# Vstock Frontend

Vstock 웹 클라이언트. **아직 초기화되지 않았다.**

공통 컨벤션(브랜치/커밋/이슈/PR)은 루트 [README.md](../README.md), 협업 프로세스는 루트 [CONTRIBUTING.md](../CONTRIBUTING.md)를 참고한다.

---

## 시작하기

프론트엔드 담당자가 스택을 확정한 뒤 이 디렉터리에서 프로젝트를 초기화한다. 루트가 아니라 **`frontend/` 안에서** 초기화해야 한다.

```bash
cd frontend
# 예시 - 실제 스택은 팀 논의 후 결정
# npm create vite@latest . -- --template react-ts
```

초기화 후 이 문서에 다음 내용을 채운다.

- 기술 스택과 버전
- 설치 / 실행 / 빌드 명령
- 환경변수 (`.env.example` 추가, 실제 `.env`는 커밋 금지)
- 백엔드 API 연동 방법 (base URL, 프록시 설정)

코드 컨벤션(컴포넌트 네이밍, 디렉터리 구조, 상태 관리 규칙 등)은 `frontend/CONTRIBUTING.md`를 새로 만들어 작성한다.

---

## 백엔드 연동

백엔드 API 서버는 `backend/`에서 별도로 실행한다. 실행 방법은 [backend/README.md](../backend/README.md) 참고.
