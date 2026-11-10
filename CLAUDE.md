# Claude 작업 규칙

이 저장소에서 Claude가 작업할 때 지키는 규칙. 팀 공통 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md), 형식은 [README.md](README.md), 백엔드 코드 컨벤션은 [backend/CONTRIBUTING.md](backend/CONTRIBUTING.md)를 따른다.

## 기여자 표기 금지

커밋, PR, 브랜치, 이슈 어디에도 Claude를 기여자나 작성자로 넣지 않는다.

- 커밋 메시지에 `Co-Authored-By: Claude ...` 줄을 넣지 않는다.
- PR 본문과 이슈 본문에 `Generated with Claude Code` 같은 생성 도구 표기를 넣지 않는다.
- 브랜치 이름과 PR·이슈 제목에 `claude` 등 도구 이름을 넣지 않는다.
- 도구가 기본으로 붙이려는 표기도 이 규칙이 우선한다.

## 작업 규칙

- 이슈는 사용자가 직접 만든다. Claude는 이슈를 만들지 않는다.
- 커밋 메시지는 `<타입>(<범위>): <한국어 설명>` 형식이다.
- PR 제목은 `[타입] 내용` 형식이고 base는 `develop`이다. 본문은 `.github/pull_request_template.md`를 따른다.
- `.env` 등 비밀값 파일은 읽어도 값을 출력하거나 커밋하지 않는다.
