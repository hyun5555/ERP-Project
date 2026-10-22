# 개인 개발용 Agency Agents 활용 기록

적용일: 2026-10-06. 설치된 역할의 전문성은 유지하고 개인 프로젝트에서 필요한 구현·검증을 수행하도록 공통 지침을 추가했다.

## 설정 변경

`~/.codex/agents`의 TOML 430개에 개인 개발 프로필을 적용했다. 원본 역할 본문과 `name`, `description`을 보존했으며 모델·권한 설정은 변경하지 않았다. 중복 파일 이름까지 포함한 파일 수이며 서로 다른 역할 430종을 의미하지 않는다.

공통 프로필은 기존 코드·표준 라이브러리·프레임워크 기능을 우선 사용하고, 개인 프로젝트에 조직별 승인 절차나 임의 SLA를 추가하지 않도록 한다. 보안·입력 검증·접근성·데이터 무결성은 유지하며 의미 있는 변경은 최소 회귀 검증을 남긴다. 요청 범위를 넘는 배포·외부 메시지·계정 변경 권한을 부여하지 않는다.

백업은 `/Users/sonhyeon-a/.codex/backups/agency-agents-personal-20261006`에 있다. `manifest.json`과 기존 `agents`, `skills` 파일을 보관했다. Agency Agents 스킬에는 개인 개발용 역할 선택·위임 예를 추가했다. 프로젝트 지침은 루트 `AGENTS.md`에 있다.

현재 대화에서 수정한 설정이 자동 재로드된다고 가정하지 않았다. 실제 담당자를 커스텀 역할 이름으로 실행하고 필요한 개인 개발 기준·파일 소유권·기대 동작을 직접 전달했다.

## 이번 작업의 실제 역할

| Agency Agent | 작업 식별자 | 책임 |
|---|---|---|
| Backend Architect | `/root/approval_concurrency` | 결재 순서·종결 상태·DB 잠금, 사원번호 발급, 관련 API·화면 |
| Realtime Collaboration Engineer | `/root/messenger_security` | STOMP 권한·CSRF, 실제 통신 검증, 재접속 이력 병합 |
| API Tester | `/root/workflow_tests` | 결재·사원번호의 독립 회귀·동시성 검증 |
| Application Security Engineer | `/root/security_review` | 변경 경로의 독립 보안·정합성 검토 |
| AI Engineer | `/root/ai_quality` | 질문별 자료 검색, AI 스트림 완료·실패 처리, SQL 문맥 회귀 검증 |
| Test Automation Engineer | `/root/browser_e2e` | 실제 브라우저 결재·첨부·세션 만료 흐름과 격리 실행 |
| Performance Benchmarker | `/root/performance_design` | API·SQL·수신자 메시지·브라우저 동일 조건 측정 설계 |
| DevOps Automator | `/root/deployment_design` | native 앱·DB·첨부 초기화·백업·새 복원 설계와 구현 대조 |

각 구현 담당은 소유 파일만 수정하고 다른 담당자의 변경을 유지한다. 주 에이전트가 코드와 검토 결과를 통합하고 전체 검증을 실행한다. 작은 후속 수정은 필요한 담당자 하나로 처리한다.

## 결재 정책

등록된 결재선의 `approval_sort` 순서대로 승인·반려한다. 앞 순서가 승인되기 전 다음 결재자는 처리할 수 없다. 문서가 반려 또는 최종 승인 상태이면 추가 처리를 차단한다. 반려 문서는 작성자가 수정·재상신하면 결재선을 다시 대기 상태로 등록한다.

처리·수정·삭제는 같은 문서 행에 `SELECT … FOR UPDATE` 잠금을 사용한다. 처리 권한은 잠금 이후 현재 결재선으로 다시 확인한다. 화면의 승인 가능 여부와 AI 대기 결재 조회도 같은 순서·문서 상태를 반영한다.

## 사원번호 정책

일반 신규 사원번호는 등록 트랜잭션에서 현재 연도와 3자리 순번으로 발급한다. 화면의 미리보기 값은 예약 번호가 아니며 신규 화면은 등록 전에 번호를 제출하지 않는다. 빈 번호 또는 기존 화면의 7자리 숫자형 번호 요청도 서버에서 실제 번호를 다시 발급한다.

연도별 `user_number_sequence` 행을 잠그고 기존 숫자형 사원번호와 마지막 발급 번호를 반영한다. 삭제된 자동 번호는 재사용하지 않는다. 한 해의 `001~999`를 소진하면 등록을 명확히 거절한다. 기존 API의 비숫자 명시 ID는 호환을 위해 유지하며 중복은 409로 반환한다.

기존 기본 설정은 앱 시작 시 `schema.sql`을 적용한다. 최신 native 개발 환경은 새 `init`에서만 seed와 schema를 적용하고 이후 앱은 `spring.sql.init.mode=never`로 실행한다. 번호 발급 이력은 DB와 함께 백업·복원하며 기존 DB나 발급 이력을 수동 초기화하지 않는다.

## 메신저 정책

HTTP 세션의 인증 사용자를 발신자로 사용하고 STOMP CONNECT에는 해당 세션의 CSRF 토큰을 요구한다. `/app/chat.send`로 메시지를 발행하고 `/app/chat.sync`로 구독 완료 신호를 요청할 수 있다. 본인의 `/user/queue/messages`, `/user/queue/read`, `/user/queue/errors`, `/user/queue/sync`만 구독할 수 있다. 직접 브로커 목적지와 임의 목적지의 발행·구독을 거절한다. 대화방 참여자 검사는 Service에서도 유지한다.

Spring 7.0.9의 순서 보장 경로는 보안 인터셉터의 거부 예외를 내부에서 처리해 기본 ERROR 응답을 생성하지 않는다. 기존 세션 속성에 CONNECT 통과·거부 상태를 기록하는 선행 인터셉터를 추가했다. Spring Security의 인증·CSRF 검사 완료 후에만 송신·구독을 허용하며, 거부 시 즉시 상태를 고정하고 기본 outbound ERROR 경로로 연결을 종료한다. CONNECT 생략 또는 잘못된 CONNECT와 SEND를 같은 WebSocket 텍스트에 이어 보내는 경로도 차단한다.

재접속 시 개인 큐를 구독하고 같은 소켓으로 동기화 요청을 보낸다. 서버는 같은 세션의 앞 구독 프레임을 처리한 뒤 요청 세션에 준비 신호를 반환한다. 신호를 받은 뒤 DB 이력을 불러와 메시지 번호로 병합한다. 이미 확인된 읽음 상태는 빈 값으로 되돌리지 않고, 이전 방의 늦은 응답은 현재 방에 적용하지 않는다. DB에 저장된 메시지의 복구가 범위이며 연결이 끊기는 순간의 미확정 발신을 자동 재전송하는 기능은 포함하지 않는다.

동시 발신은 방 행을 먼저 갱신·잠근 뒤 메시지를 저장한다. 같은 방의 두 참가자가 메시지 FK 공유 잠금에서 방 갱신 잠금으로 동시에 전환하는 경합을 피한다. 메시지 저장 트랜잭션이 끝난 뒤 개인 큐로 전달한다.

기존 Spring SimpleBroker 스케줄러로 10초 heartbeat를 협상하도록 설정했다. 이번 검증은 실제 소켓 종료·재연결·이력 복구와 heartbeat 협상 값까지 확인한다. 장시간 네트워크 단절 상태에서 heartbeat가 연결을 감지하는 실험과 브라우저 화면 검증은 이번에 수행하지 않았다.

## 검증

전체 백엔드: `./mvnw --batch-mode test` — Testcontainers MySQL과 실제 서버의 WebSocket 연결을 사용한다.

메시지 병합: `node --test frontend/src/messenger/messages.test.js`

프론트엔드: `frontend`에서 `npm run build`

2026-10-06 최종 로컬 검증: Maven 56개(업무 흐름 25개, 실제 WebSocket 18개, 모델 HTTP 스트림 12개, 브라우저 실행 준비 1개) 통과, 실패·오류 0개. 실제 모델 평가는 기본 실행에서 1개 건너뛰고 별도 실행했다. CONNECT 생략 또는 CSRF 누락·위조 뒤 SEND·SUBSCRIBE를 같은 텍스트에 이어 보내는 공격도 실제 ERROR 프레임·종료 코드 1002·DB 미저장으로 확인했다. Node 7개(메시지 병합 3개, AI SSE 4개)와 Vite 빌드가 통과했다. 브라우저는 5개 흐름 × 2회 10/10 통과하고 전체 Maven의 기본 5개도 재확인했다.

실제 모델은 질문 8개 × 2회 × 두 모델로 최종 32건을 검증했다. 기본 모델은 `qwen3.5:2b`이며 질문 영역·권한·번호·제목에 맞는 자료만 전달한다. 자료가 없거나 지원 범위 밖인 질문과 명시적 우선순위 요청은 모델 없이 안내한다. [AI 품질·브라우저 핵심 흐름](ai-quality-browser-flows.md)에 비교 전 오답, 보완, 최종 수치·원문과 CI 재현 명령을 남겼다. GitHub Actions에 Chromium 설치·전체 실행·실패 기록 보관을 구성했으며 원격 CI 실행은 이번에 수행하지 않았다.

에이전트 설정 TOML 430개의 파싱, 원본 역할 본문·이름·설명 보존, 설치 파일과 검토한 변경본의 일치를 확인했다. Agency Agents 스킬 구조 검증도 통과했다.

## 5·6단계 설계·독립 검토·native 실행

설계 담당은 실제 Mapper·Service·브라우저 호출과 데이터 경계를 기록했다. Application Security Engineer는 전체 백엔드와 새 관리 도구를, Test Automation Engineer는 전체 프런트와 자동 검증 범위를 독립 검토했다. 각 담당은 소유 문서만 수정했고 소스·DB·빌드 제어는 주 에이전트가 순차 수행했다. [통합 결과](stage5-6-design-review.md)에 실제 담당·정적 발견·재현·수정·미검증을 구분했다.

최신 개인 개발 방식은 Docker 없는 `scripts/dev-environment.py` 관리 환경이다. 기존 Testcontainers 회귀는 별도 실행 범위로 유지한다. `scripts/verify-dev-recovery.py`로 전용 DB 재기동, 실제 첨부·권한, 앱 정지 백업, 새 인스턴스의 schema/행/파일 일치와 복원 후 동시 번호 발급을 확인했다. 독립 리뷰에서 발견한 경로·symlink·복원 계정·동시 관리·metadata 캐시·mysql client 명령·전역 설정·Java 환경변수 경계는 공통 도구에서 보완하고 실제 guard를 실행했다.

전체 회귀와 native 핵심 업무 통과가 제품의 모든 경계 통과를 뜻하지 않는다. 10월 6일 추가 probe로 기존 세션의 강등·퇴직 권한 유지, 본인 결재, 사원 cascade 삭제를 재현했고 비밀번호 변경 뒤 form reset 오류도 native 브라우저에서 확인했다. 당시 미수정으로 남긴 세 P1은 아래 후속 작업에서 수정했다. form reset 오류는 남아 있다. 로그아웃 이후 WebSocket 발신 가설은 close 1008·미저장 실증 후 철회했다.

대량 성능은 [동일 조건 측정 설계](performance-measurement-design.md)를 완료했으며 실제 반복 측정은 다음 구현 범위다. 운영 HTTPS·다른 머신 재현·외부 백업은 아직 수행하지 않았다. 실행 명령은 [native 개발·복원 설계](deployment-readiness-design.md), 실제 결과는 [업무·복구 JSON](review-evidence/native-recovery.json)과 [최종 복원 JSON](review-evidence/native-final-restore.json)을 따른다.

## 2026-10-22 P1 후속 구현

실제 Backend Architect(`/root/p1_backend`)가 세션·업무 Service·Mapper·FK 전환을 담당하고 API Tester(`/root/p1_regression`)가 두 통합 테스트 파일에 회귀 14개를 추가했다. Application Security Engineer(`/root/p1_review`)는 권한·로그인 경합·데이터 보존을 독립 검토했다. 주 에이전트가 테스트·빌드·DB·앱 제어를 순차 실행했다. 같은 쿠키 재로그인과 회수 커밋의 경합도 수정·회귀 검증했다.

Maven 71개 중 70개 통과·선택 모델 평가 1개 생략, Node 7개와 실제 브라우저 5개 흐름 통과다. 기존 native DB 행·첨부 보존, 새 WAR 반영, 전환 후 백업의 새 복원 일치를 확인했다. [P1 설계·구현·한계](p1-session-approval-history.md)와 [실행 근거](review-evidence/p1-20261022.json)에 현재 상태를 남겼다.

## 2026-10-22 데이터·사용성과 포트폴리오 측정

Backend Architect(`/root/data_backend`), Frontend Developer(`/root/usability_frontend`), Performance Benchmarker(`/root/performance_tools`)가 파일 소유권을 나눠 구현했다. Application Security Engineer(`/root/data_independent_review`)가 제품 경계·브라우저 회귀와 실제 결과를, API Tester(`/root/performance_independent_review`)가 측정 조건과 1천 건 원문을 독립 검토했다. 주 에이전트가 빌드·DB·앱·측정을 순차 제어했다.

의존성 감사 0건, Maven 80개 통과·선택 2개 생략, 브라우저 9개·Node 7개 통과다. 1천 건 API 9,900개·메시지 전달 1,800개·브라우저 360개 표본은 실패 0이며 요약 126개 그룹을 독립 재계산했다. 사용자 지시로 추가 규모를 중단하고 전용 환경을 모두 종료했으며 기본값도 1천 건으로 낮췄다. [정책·한계·실행 결과](data-usability-performance.md)에 남겼다.

## 후속 요청 예

“Backend Architect로 결재 수정 흐름을 보완하고 API Tester로 해당 회귀 테스트를 검증해줘. 기존 구조를 유지해줘.”

“Realtime Collaboration Engineer로 메신저 기능을 수정하고 Application Security Engineer로 바뀐 권한 경로만 검토해줘.”

“이 프로젝트에 맞는 Agency Agents를 골라 활용해줘. 작은 작업은 하나의 담당자로 처리하고 변경 내용과 검증 결과를 알려줘.”

설정 방식은 [OpenAI 공식 커스텀 에이전트 문서](https://learn.chatgpt.com/docs/agent-configuration/subagents), 메시지 권한은 [Spring Security 공식 WebSocket 문서](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)를 참고했다.
