# ERP-Project

### Spring Boot·React 기반 기업 업무 플랫폼

## 📌 프로젝트 소개

<p align="center">
  <img width="1222" height="523" alt="Image" src="https://github.com/user-attachments/assets/cdcbc522-fabd-4792-bea0-674f7fcca544" />
  <img width="1144" height="541" alt="Image" src="https://github.com/user-attachments/assets/de9e77ba-7c87-4060-b48b-d46a21d92c33" />
  <img width="958" height="520" alt="Image" src="https://github.com/user-attachments/assets/ab251831-3a4d-4c4c-b869-f820fe7724e5" />
</p>

EZENT ERP는 기업 내 전자결재, 공지사항, 사원관리, 1:1 메신저를 통합하여
문서 처리와 사내 협업을 지원하는 업무 플랫폼입니다.

본 프로젝트는 실제 기업 전자결재 시스템의 업무 흐름을 이해하고,
프론트엔드와 백엔드를 모두 구현하며 협업 기반 웹 개발 경험을 쌓기 위해 진행되었습니다.

## 로컬 실행

개인 개발은 Docker 없이 Java 21·native MySQL 8.4·Python 3.11 이상으로 실행합니다. 앱·DB 데이터·첨부는 `.local-dev/source` 안에서 분리되고 앱과 DB는 `127.0.0.1`의 별도 포트만 사용합니다. 초기화는 새 환경에서 한 번만 수행합니다.

```bash
npm --prefix frontend ci
npm --prefix frontend run build
./mvnw --batch-mode -DskipTests package
python3 scripts/dev-environment.py init
python3 scripts/dev-environment.py start
python3 scripts/dev-environment.py status
```

`status`가 실제 접속 주소를 출력합니다. MySQL 실행 파일 기본 경로는 `/opt/homebrew/opt/mysql@8.4/bin`이며 다른 환경에서는 `--mysql-bin /설치경로/bin`을 지정합니다. macOS 설치 명령은 `brew install mysql@8.4`이며 시스템 서비스를 시작할 필요가 없습니다. 생성된 비밀번호·DB·첨부·백업은 Git에 넣지 않습니다.

```bash
python3 scripts/dev-environment.py check
python3 scripts/verify-dev-recovery.py
python3 scripts/dev-environment.py stop
```

복구 검증은 전용 개발 계정·결재·첨부를 만들고 새 독립 환경에 복원합니다. 원본과 복원본의 스키마·행·첨부를 비교하고 복원본에서 동시 번호 발급을 확인합니다. 기존 환경에 복원하는 요청은 거부합니다. 세부 명령과 검증 범위는 [개발 환경·복구 설계](docs/refactoring/deployment-readiness-design.md), 확인된 코드 문제와 결과는 [5·6단계 통합 검토](docs/refactoring/stage5-6-design-review.md)에 있습니다.

2026-10-22에 기존 세션 권한 회수·본인 결재 차단·사원 이력 보존을 구현하고 현재 native 환경에 반영했습니다. 기존 DB는 재초기화하지 않고 백업 후 [사용자 참조 FK migration](document/migrations/20261021-preserve-user-history.sql)을 적용합니다. 쓰기 중지·전환·재개 절차와 실제 보존·회귀 결과는 [P1 설계·구현 기록](docs/refactoring/p1-session-approval-history.md)을 따릅니다.

React 소스를 수정한 경우 Spring Boot 실행 전에 프론트엔드를 빌드합니다.

```bash
cd frontend
npm ci
npm run build
```

로컬 AI 챗봇까지 확인하려면 별도 터미널에서 기본 모델을 준비합니다.

```bash
ollama pull qwen3.5:2b
ollama serve
```

모델 서버 주소와 모델은 `ERP_AI_BASE_URL`, `ERP_AI_MODEL` 환경 변수로 변경할 수 있습니다. Ollama가 꺼져 있어도 ERP의 다른 업무 기능은 실행됩니다.

- 테스트 계정: `admin` / `1234`
- MySQL: 전용 랜덤 포트·데이터베이스 `erp`, 앱용 DML 계정과 무작위 비밀번호
- 종료: `python3 scripts/dev-environment.py stop`

기존 VS Code `F5`·Compose 설정은 과거 Docker 개발 방식으로 남겨 두었습니다. 위 native 실행은 이 설정을 사용하지 않습니다.

의존성 High 2건과 첨부·목록·메신저·암호/로그인 사용성 문제도 보완했습니다. [최신 구현·검증 기록](docs/refactoring/data-usability-performance.md)에 전체 회귀 80개·브라우저 9개와 [포트폴리오용 1천 건 측정 결과](performance/results/20261022T103945Z-2fe863/README.md)를 정리했습니다. 성능 도구 기본값은 1천 건입니다.

포트폴리오 제출 전 남은 작은 개발과 README·시연 자료 마감은 [남은 작업 목록](docs/refactoring/portfolio-readiness.md)에 정리했습니다.

## 기존 전체 회귀 검증

아래 JUnit 회귀는 Testcontainers를 사용하므로 Docker가 필요합니다. 위 native 개발·복구 검증과 별도 실행 경계입니다.

```bash
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
cd frontend && npx playwright install chromium && cd ..
./mvnw --batch-mode test
```

회귀 테스트는 Testcontainers의 격리 MySQL로 인증·권한·전자결재·사원번호 동시 발급·실제 WebSocket 송수신과 재접속을 확인합니다. AI는 질문별 SQL 문맥, 실제 HTTP 스트림의 완료·오류·본문 제한 시간, Node SSE 처리를 검증합니다. Chromium에서 로그인 → 결재 작성 → 순차 승인과 첨부 다운로드, 세션 만료 후 읽기·쓰기의 로그인 복귀와 데이터 보존도 확인합니다. 개발용 MySQL 데이터는 변경하지 않습니다.

전체 Maven 검증에 브라우저 흐름이 포함됩니다. 반복 검증은 `ERP_E2E_REPEAT=2 ./mvnw --batch-mode -Dtest=BrowserFlowIntegrationTest test`로 실행합니다. 실제 AI 모델 비교는 Ollama가 실행된 환경에서 별도 수행하며, 질문·답변 원문과 실패율·응답시간을 저장합니다. 기본 CI는 실제 모델 설치를 요구하지 않습니다.
과거 `scripts/benchmark.sh`는 현재 로그인 화면에서 CSRF를 찾지 못하며, HTML 셸 응답만 측정하므로 새 성능 검증에 사용하지 않습니다. 대량 API·SQL·메신저·브라우저 측정 기준은 [실제 성능 측정 설계](docs/refactoring/performance-measurement-design.md)에 정리했습니다. 기존 결과는 `performance/results/`에 보존합니다.

전체 리팩터링 결과, 검증된 성능 수치와 트러블슈팅은 [리팩터링 종합 기록](docs/refactoring/refactoring-summary.md)에 정리했습니다.
전자결재 리팩터링 구조, 테스트 범위, 트러블슈팅과 Before/After 수치는 [전자결재 리팩터링 기록](docs/refactoring/approval-refactoring.md)에 정리했습니다.
권한 강화와 공지사항·사원관리 리팩터링 기록은 [권한·공지·사원관리 리팩터링 기록](docs/refactoring/access-notice-user-refactoring.md)에 정리했습니다.
React/Vite 전환 범위와 화면 캡처, 성능·트러블슈팅 기록은 [프론트엔드 React 전환 기록](docs/refactoring/frontend-react-transition.md)에 정리했습니다.
1:1 사내 메신저의 구조, 권한, 재접속과 검증 내용은 [사내 메신저 MVP 기록](docs/refactoring/messenger-mvp.md)에 정리했습니다.
로컬 LLM 챗봇의 권한별 문맥, 스트리밍, 장애 처리와 측정 기준은 [로컬 LLM 챗봇 MVP 기록](docs/refactoring/local-llm-mvp.md)에 정리했습니다.
질문에 맞는 AI 자료 검색, 실제 모델 반복 비교와 브라우저·CI 재현 방법은 [AI 품질·브라우저 핵심 흐름 기록](docs/refactoring/ai-quality-browser-flows.md)에 정리했습니다.
개인 개발에 사용할 Agency Agents 역할, 결재·사원번호·메신저 정책과 검증은 [개인 개발용 에이전트 활용 기록](docs/refactoring/solo-agent-workflow.md)에 정리했습니다.

---

## 📅 프로젝트 기간

* 2025.03 ~ 2025.04

---

## 👥 팀 구성

| 이름  | 역할 | 담당 업무                                  |
| --- | -- | -------------------------------------- |
| 손현아 | 팀원 | 화면 설계 및 편집, DB 설계, 기능 구현, 프론트엔드/백엔드 개발 |
| 이안나 | 팀장 | 문서 관리 및 총괄, 기능 구현, 프론트엔드/백엔드 개발        |
| 왕석진 | 팀원 | 화면 설계 및 편집, DB 설계, 기능 구현, 프론트엔드/백엔드 개발 |
| 정상윤 | 멘토 | 프로젝트 피드백 및 기술 질의응답                     |

---

# 🧑‍💻 프로젝트 수행 요소

## ✔ 결재선 시스템

* 결재자 추가/삭제 기능
* 등록된 결재선 순서대로 승인·반려 처리
* 팝업 기반 결재자 선택 UI 구현

## ✔ 검색 기능

* 제목 기반 검색
* 사원번호 및 이름 검색
* 상태별 필터링 검색

## ✔ 페이징 처리

* 목록당 10개 데이터 출력
* 페이지 이동 기능 구현

## ✔ 권한 관리

* 관리자 기능 분리
* 사용자 권한별 접근 제어

---

# 🛠 기술 스택

## Backend

* Java
* Spring Boot 4.1.1
* MyBatis
* Spring Security
* Spring WebSocket / STOMP SimpleBroker

## Frontend

* React 19 / Vite 8
* 반응형 CSS / 다크모드
* Spring Boot 정적 리소스의 단일 React 셸(`index.html`)

## Database

* MySQL

## Tools

* Visual Studio Code
* Git / GitHub Actions
* Docker / Testcontainers

---

# 💡 DB 설계

<p align="center">
  <img width="713" height="535" alt="Image" src="https://github.com/user-attachments/assets/d86fdd61-12da-41c7-b009-f23c3d424226" />
</p>

# 📋 주요 기능

## 🔄 전자결재 업무 흐름

```text
결재 작성
   ↓
결재선 지정
   ↓
결재 요청
   ↓
결재자 승인/반려
   ↓
결재 상태 변경
   ↓
최종 승인 시 전체 열람 가능
```

---


### ✔ 주요 기능

* 로그인 실패 시 Alert 처리
* 관리자 초기 비밀번호 변경 유도
* 사용자 정보 자동 로딩

## 1. 로그인 및 사용자 인증

* 사원번호 기반 로그인
* 비밀번호 검증 및 재설정 기능
* 관리자 임시 비밀번호 처리
* 세션 기반 사용자 인증 관리

## 2. 전자결재 시스템

기업 내부 결재 프로세스를 웹 기반으로 구현한 핵심 기능입니다.

### ✔ 지원 문서

* 기안서
* 품의서
* 연차신청서

### ✔ 주요 기능

* 결재 문서 작성
* 결재선 추가 및 삭제
* 첨부파일 업로드
* 결재 승인 / 반려 처리
* 결재 상태 관리
* 결재 검색 및 페이징 처리

### ✔ 결재 상태

* 대기중
* 진행중
* 승인
* 반려

### ✔ 특징

* 앞 결재자의 승인 이후 다음 결재 처리, 반려·최종 승인 후 추가 처리 차단
* 반려 시 코멘트 필수 작성
* 승인 완료 문서 전체 열람 가능
* 작성자와 결재자 간 알림 흐름 구현

---

## 3. 내 결재 관리

사용자가 작성하거나 수신한 결재 문서를 상태별로 관리할 수 있습니다.

### ✔ 제공 기능

* 결재대기
* 결재진행
* 결재반려
* 결재승인
* 결재수신
* 결재작성

### ✔ 상세 기능

* 문서 검색
* 문서 상태 필터링
* 문서 종류별 조회
* 최신순 정렬
* 페이징 처리

---

## 4. 전체 승인 내역

최종 승인된 문서를 전사원이 조회할 수 있는 기능입니다.

### ✔ 주요 기능

* 부서별 조회
* 문서구분별 조회
* 검색 기능
* 페이지네이션
* 승인 완료 문서 열람

---

## 5. 공지사항 시스템

사내 공지사항을 등록하고 관리할 수 있는 기능입니다.

### ✔ 주요 기능

* 공지 등록 및 삭제
* 중요 공지 설정
* 메인 노출 여부 설정
* 부서별 공지 조회
* 첨부파일 업로드
* 검색 기능

### ✔ 특징

* 관리자만 등록/삭제 가능
* 공지 대상과 관계없이 전체 열람 가능
* 중요도 및 최신순 정렬 지원

---

## 6. 사원관리 시스템

사원 정보를 관리하고 조회할 수 있는 기능입니다.

### ✔ 주요 기능

* 전체 사원 조회
* 사원 검색
* 부서/직급/근무상태 필터링
* 사원 등록/수정/삭제
* 권한 기반 정보 접근 제어

### ✔ 특징

* 관리자와 일반 사용자 권한 분리
* 주민번호 기반 생년월일 및 성별 처리
* 자동 생성 사원번호 기능

---

## 7. 사내 메신저

Spring WebSocket, STOMP SimpleBroker, MySQL로 구현한 1:1 메신저 MVP입니다.

### ✔ 주요 기능

* 실시간 텍스트 메시지
* 대화 이력 저장 및 재접속 복구
* 읽음 처리 및 안 읽은 메시지 수
* 대화방 참여자 권한 검사

---

# 📖 프로젝트를 통해 배운 점

* 웹 개발 전반의 Lifecycle 경험
* 요구사항 분석 및 DB 설계 경험
* 프론트엔드와 백엔드 통합 개발 경험
* 협업 및 형상관리의 중요성 체감
* 실무형 전자결재 프로세스 이해

---

# 👏 프로젝트 회고

이번 프로젝트를 통해 단순 CRUD 구현을 넘어
실제 기업 환경에서 사용되는 전자결재 프로세스를 웹 시스템으로 구현해볼 수 있었습니다.

특히 결재선 처리, 권한 관리, 검색 및 페이징 처리 등의 기능을 직접 설계하고 구현하면서
실무형 웹 서비스 개발 경험을 쌓을 수 있었고,
협업 과정에서 형상관리와 커뮤니케이션의 중요성 또한 깊게 경험할 수 있었습니다.
