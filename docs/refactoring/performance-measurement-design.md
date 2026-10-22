# 실제 성능 측정 설계

최신 결정(2026-10-22): 생성기와 API·SQL·메신저·브라우저 도구를 구현하고 [포트폴리오용 1천 건·3반복 결과](../../performance/results/20261022T103945Z-2fe863/README.md)를 확보했다. 사용자가 추가 규모 중단을 요청해 전용 환경을 모두 종료했다. 기본 실행도 1천 건이다. 아래 1만·10만 건과 운영 규모 항목은 과거 설계의 선택 가능 범위이며 이번 완료 기준이 아니다.

2026-10-06 기준 단계 5의 설계다. 이 문서는 현재 코드의 호출 경로를 검토해 작성했다. 대량 데이터 생성, 부하 실행, SQL 실행 계획 수집, 브라우저 성능 계측은 아직 수행하지 않았다. 과거 HTML 응답 측정과 현재 기능 테스트를 실성능 결과로 대체하지 않는다.

## 1. 측정 범위와 기존 기록의 해석

`scripts/benchmark.sh`는 로그인 후 `/main.do`, `/notice/list.do`, `/approval/list.do`, `/user/list.do`를 순차 호출한다. 현재 네 경로 모두 `index.html`을 반환한다. JavaScript 실행과 이후 API 조회는 측정하지 않는다. 기존 수치는 해당 시점의 서버 HTML 응답 기록으로 보존한다.

새 측정은 다음 경계를 분리한다.

| 경계 | 시작 → 종료 | 포함 범위 |
|---|---|---|
| API | 인증된 요청 시작 → 본문 수신·검증 완료 | 네트워크, 서버, SQL, 직렬화; 로그인·워밍업 제외 |
| SQL | 실제 Mapper SELECT 실행 | 실행 계획의 행 수·반복·시간; API 시간과 직접 합산하지 않음 |
| 메신저 | SEND 호출 직전 → 수신자의 해당 MESSAGE 처리 | DB 저장과 실제 WebSocket 전달; 발신자 echo는 별도 |
| 브라우저 | 문서 탐색·실제 입력 → API가 제공한 paint entry 또는 기대 DOM 준비 | 번들, API, React 처리, DOM; 관측 paint와 DOM 준비 시각을 구분 |

## 2. 고정 환경과 데이터

기존 개발·운영 DB를 사용하지 않는다. 새 개발 환경과 성능 환경은 native MySQL 8.4의 전용 datadir·포트, 빌드한 WAR의 전용 앱 포트, 전용 업로드 디렉터리로 분리한다. 개발용 루트와 성능 측정용 루트도 별도로 두고, 대량 fixture는 성능용 루트에서만 생성한다. 두 루트의 절대 경로·datadir·MySQL 포트/소켓·앱 포트·업로드 경로가 겹치지 않는지 시작 전에 확인하고 DB 접속 대상·계정 권한도 기록한다. 기존 MySQL 인스턴스나 파일을 초기화·복구 대상으로 삼지 않는다.

환경의 초기화·시작·중지·상태 확인·백업·복구·검증은 주 에이전트가 추가하는 `scripts/dev-environment.py`의 `init/start/stop/status/backup/restore/check` 경계로 관리한다. 구체적인 경로·포트·실행 명령은 해당 스크립트와 실행 시 생성되는 manifest를 기준으로 기록한다. 대량 데이터 생성 전 대상이 성능용 루트인지 검증하고, 초기화·복구는 그 루트에서 관리하는 DB·첨부에만 적용한다. 백업 복구 후 테이블 건수·기준 문서 상태·첨부 바이트를 확인해 원래 fixture 상태에서 반복을 시작한다.

2026-10-06에 이미 수행한 Testcontainers 기반 회귀 기록은 당시 환경의 검증 증거로 유지한다. 그 기록은 새 native 환경의 성공을 대신하지 않는다. 새 개발·성능 실행은 Docker를 전제로 하지 않으며, 기존 JUnit의 테스트 계정·HTTP/STOMP 요청·완료 신호를 재사용할 때 컨테이너 부트스트랩은 가져오지 않는다. 성능 실행을 명시적으로 선택한 경우에만 데이터 생성과 부하가 시작되도록 하고 기본 CI 기능 테스트와 분리한다.

한 실행의 조건은 `manifest.json`에 고정한다. 코드 revision과 미커밋 diff 해시, Mapper 해시, fixture seed `20261006`, 실제 테이블별 행 수, 프로필, Java·native MySQL 8.4·Node·Playwright·Chromium 버전과 사용 바이너리 경로, OS·CPU·메모리·프로세스 자원 조건, 전용 환경 루트·datadir·DB 이름·포트/소켓·앱 포트·업로드 경로, DB 설정·인덱스, Hikari 크기, JVM 옵션, WAR·번들 해시와 바이트, URL, 쿠키·연결 재사용, 병렬 수, timeout, 워밍업·반복·캐시 조건을 기록한다. 비밀번호·쿠키·CSRF·개인정보는 기록하지 않는다.

| 데이터 | 기본 설계 | 별도 편중 프로필 |
|---|---|---|
| 공지 | 1천 → 1만 → 10만 건; 중요 5%, 팀별 코드·전사 999를 고정 비율로 생성 | 검색어가 전체의 50% 또는 1%에 등장 |
| 결재 | 각 규모 N건; 승인 60%, 대기 20%, 진행 10%, 반려 10%; 문서당 결재선 3개 | 기준 계정에 기안·수신 문서 50% 집중 |
| 사원 | 기본 100명, 개발·디자인·경영지원 60:25:15; 별도 사원 조회 실험에서만 1천/1만 명 | 같은 부서·직급에 80% 집중 |
| 메신저 | 100개 방에 총 N개 메시지; 읽음 80%, 안 읽음 20% | 메시지 80%를 한 방에 집중 |
| 내용·첨부 | 제목·본문의 실제 UTF-8 바이트 고정; 결재 5%에 1개 파일; 다운로드는 별도 고정 1MiB 파일 | 큰 본문·2천 자 메시지는 별도 사례 |

승인 문서는 모든 결재선을 승인으로, 진행·반려 문서는 순서와 상태를 일치시켜 생성한다. 순서가 아직 오지 않은 수신 문서도 포함한다. 무결성이 깨진 fixture를 성능 데이터로 쓰지 않는다. 사원번호는 테스트용 문자열 식별자를 써 연도당 자동 번호 999개 제한을 우회하지 않으며, 번호 발급 성능과 단순 대량 삽입을 혼합하지 않는다.

규모별로 먼저 실제 행 수·상태 분포·검색 결과·접근 권한을 검증하고, 같은 seed와 데이터에서 3회 반복한다. 읽기 실험 동안 데이터를 변경하지 않는다. 쓰기·읽음 처리·메신저는 각 반복 전 원래 상태로 재생성한다. fixture 생성 시간은 서비스 시간에서 제외한다. 10만 건에서 timeout 또는 자원 부족이 나면 원인을 기록하고 중단하며, 완료하지 않은 규모를 통과로 표시하지 않는다.

## 3. API·SQL 측정

| 사례 | 실제 API | 확인할 Mapper·호출 |
|---|---|---|
| 업무 홈 | `GET /ERP/api/dashboard` | `ApprovalService.getCounts`의 5회 COUNT + `selectMainNotices` |
| 공지 목록 | `GET /ERP/api/notices?page=…&notice_team=…&searchWord=…` | `countNotices`, `selectNotices` |
| 내 결재 | `GET /ERP/api/approvals?page=…&status=…&keyword=…` | `countAppList`, `selectAppList` |
| 결재 수신 | `GET /ERP/api/approvals/received?page=…` | `countRecvApprovalList`, `selectRecvApprovalList` |
| 전체 승인 | `GET /ERP/api/approvals/completed?team=…&page=…` | `selectListAllCount`, `selectListAll` |
| 사원 목록 | `GET /ERP/api/users?page=…&team=…&searchKey=name&searchWord=…` | `countUsers`, `selectUsers` |
| 대화방·기록 | `GET /ERP/api/chat/rooms`, `GET /ERP/api/chat/rooms/{roomNo}/messages` | `selectRooms`, `selectMessages` |

첫 페이지, 결과가 존재하는 깊은 페이지, 팀·상태 필터, 흔한/드문/없는 검색어를 구분한다. 깊은 페이지는 fixture의 실제 총 페이지 수 이내에서 100·1000 또는 마지막 페이지를 선택하고 manifest에 넣는다. 빈 페이지는 별도 정확성 사례다. 목록의 건수·정렬·권한·페이지 중복, `totalCount`와 fixture 기대값까지 검사한다. 200 상태만으로 성공 판정하지 않는다.

최소 실행 방식은 기존 Java HttpClient와 JUnit이다. 이미 시작한 native 성능 환경의 URL을 명시적으로 주입하고 기존 테스트의 Docker 서버 생성과 분리한다. 클라이언트당 로그인·세션·HttpClient를 유지한다. 기본 병렬 수 1과 5를 별도 실행하고, 각 작업자에서 사례당 워밍업 5회 후 50회 × 3반복한다. 요청 간 think time, 사례 순서와 정지 조건을 고정한다. 실제 완료 요청 수와 전체 경과시간으로 처리량을 산출하되, 이 방식은 응답을 기다리는 closed-loop이므로 느릴수록 송신률이 감소한다. 고정 도착률 부하나 최대 수용 인원의 증거로 해석하지 않는다.

각 요청에 고정 제한 시간을 설정한다. timeout·연결 실패·302 로그인 이동·403·500·JSON 불일치를 모두 시도 분모에 포함한다. 예정/시도/완료/정상/실패를 따로 기록하고 중단 이유도 저장한다. 실패까지 포함한 경과시간 분포와 성공 요청 지연 분포를 분리하며, timeout은 제한시간에 잘린 표본으로 표시한다.

API와 같은 파라미터의 실제 MyBatis BoundSql·바인딩 값으로 SELECT를 추출한다. 먼저 `SHOW CREATE TABLE`, `SHOW INDEX`, 테이블 건수와 통계 조건을 보존하고, `EXPLAIN FORMAT=JSON` 및 `EXPLAIN ANALYZE FORMAT=TREE` 원문을 저장한다. 후자는 쿼리를 실제 실행하므로 격리 DB의 읽기 SELECT에만 사용한다. 추정 행 수, 실제 행 수·loops·시간, 정렬·임시 결과를 대조한다. 계측 실행 시간은 사용자 API 지연 표본에 섞지 않는다. [MySQL 8.4 EXPLAIN 문서](https://dev.mysql.com/doc/refman/8.4/en/explain.html).

필요한 느린 사례에만 JDK Flight Recorder와 MySQL Performance Schema를 추가 관측한다. 별도 설치나 항상 켜지는 운영 계측부터 도입하지 않는다. API가 느렸다는 이유만으로 SQL 병목을 단정하지 않는다.

## 4. 실제 메시지 전달·복구

기존 `MessengerWebSocketIntegrationTest`의 로그인, CSRF CONNECT, `/user/queue/messages` 구독, `/app/chat.sync` 준비 응답을 재사용한다. 구독이 완료되기 전 메시지를 보내지 않는다. SEND는 `/app/chat.send`를 사용하며, 허용하지 않은 구독·송신의 차단 검증은 기능 테스트를 유지한다.

한 JVM에서 발신·수신 Peer를 실행해 같은 `System.nanoTime()` 시계로 측정한다. 메시지 내용에 실행 ID와 연속 번호를 넣어 발신 전 시각과 서버 `messageNo`를 대응시킨다. 서버의 초 단위 `sentAt`이나 서로 다른 브라우저의 `performance.now()`를 빼서 전달 지연을 계산하지 않는다.

기본은 단일 방의 두 Peer에서 왕복 방향을 번갈아 보내는 100건 × 3반복, 별도 사례는 5개 독립 방·10개 Peer에서 동시에 각 100건이다. 큰 방의 과거 기록 N과 신규 전송 수를 분리한다. 발신자 echo, 수신자 도착, 읽음 영수증은 다른 지표다. 기존 5초 대기 제한을 진단 deadline으로 고정하고, 그 값 자체를 운영 SLA로 표현하지 않는다.

모든 기대 메시지를 제한 시간까지 기다린 뒤 수신 callback 횟수, 고유 `messageNo`, payload 연속 번호, DB 저장 건수를 대조한다. 유실/중복/내용 불일치/순서 오류/DB 오류를 각각 기록한다. UI의 중복 제거 이후 건수만 보고 전달 중복을 숨기지 않는다. 부하 중 오류·마지막 메시지의 완료 신호와 자원 해제를 확인한다.

재접속은 전송 중 transport를 끊고, 끊긴 동안 저장한 고정 수의 메시지가 sync 이후 기록 API에서 복구되는지 확인한다. 감지 지연, 연결 완료, 기록 수신, UI 표시 시간을 분리한다. 현재 클라이언트의 3초 재연결 대기와 10초 heartbeat 조건도 기록한다. 이 브로커는 오프라인 MESSAGE를 나중에 재송신하는 구조가 아니므로, 오프라인 미수신을 온라인 전달 유실로 집계하지 않는다. DB 저장 실패와 복구 실패는 별도 실패다.

실제 브라우저 메신저에서는 callback 수신 외에 메시지의 DOM 표시까지 별도로 측정한다. `Messenger.jsx`가 메시지마다 수행하는 대화방·읽음 API 요청 횟수도 기록해, transport만 빠르고 화면은 늦는 경우를 구분한다.

## 5. 브라우저 로딩·반응성

설치된 Playwright Chromium과 production Vite 번들을 사용한다. 기능용 `core-flow.spec.js`의 테스트 시간을 성능 지표로 쓰지 않고, 별도 성능 시나리오에서 재사용 가능한 로그인·사용자 흐름만 가져온다. workers 1, retries 0, viewport 1440×900, 브라우저 버전·headless 모드·호스트 조건을 고정한다. 외부 모델 호출은 제외한다.

로그인 세션을 준비한 뒤 새로운 context의 첫 문서 탐색을 cold browser 사례로, 같은 context의 재탐색을 warm 사례로 분리한다. cold browser가 DB·OS 디스크 캐시까지 초기화했다는 뜻은 아니다. 네트워크·CPU 제한을 적용한 추가 사례는 원래 조건과 별도 결과로 저장한다. 각 화면 조건에서 워밍업 후 10회 × 3반복한다.

`addInitScript`로 문서 실행 전에 PerformanceObserver를 설치한다. 지원되는 entry type을 기록하고, 미지원·미관측 값을 0 대신 `null`과 이유로 저장한다. `buffered:true`와 type별 observer를 사용해 navigation/resource, largest-contentful-paint, layout-shift, event, longtask의 원문을 수집한다. 각 사례의 관측창 시작·끝 시각을 `performance.now()`로 보존하고, 종료 시 observer의 callback 결과와 `takeRecords()`에 남은 기록을 함께 반영한다. 수집·직렬화 시각과 entry의 발생 시각을 구분하고, 창을 넘어 완료된 event는 경계 표본으로 표시한다. 종료 처리 중 생긴 entry를 다음 사례에 섞지 않는다. [PerformanceObserver 문서](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceObserver).

| 지표 | 종료·계산 조건 | 해석 |
|---|---|---|
| LCP | 최초 실제 입력 전에 마지막 후보; 배경 탭 제외 | 문서 첫 로딩의 관측 LCP. SPA 내부 화면 전환마다 새 LCP라고 부르지 않음 |
| 업무 준비 | navigation 시작 → fixture 대상 ID·총 건수·문서 상태와 대응하는 DOM 확인, 후속 rAF 시각을 별도 기록 | API 결과가 반영된 DOM 준비 시간; rAF가 실제 paint 완료 시각은 아님 |
| CLS | `hadRecentInput=false` entry; 간격 1초 미만·최대 5초 window 중 최대 합 | 고정 방문 길이에서 관측한 CLS. 단순 전 entry 합계 아님 |
| 입력 반응 | 실제 click/keypress의 event duration, input delay, processing 시간 | 다음 paint까지의 반응; API 완료·후속 상태 표시까지의 시간을 포함하지 않음 |
| 업무 처리 | 클릭 시각 → 대상 ID의 승인 상태·기대 검색 결과 건수·고유 메시지 ID에 대응하는 DOM 확인 | API와 비동기 UI를 포함한 결과 준비 시간; 후속 rAF도 실제 paint로 표현하지 않음 |
| longtask·자원 | 관측 기간의 개수·누적시간, 번들/API byte·duration | 병목 위치를 좁히는 근거; longtask 합을 INP라고 부르지 않음 |

LCP는 입력 후 추가 후보가 보고되지 않는 점을 반영한다. CLS는 관측 방문 길이를 고정하고 보고서에 명시한다. [LCP 정의](https://web.dev/articles/lcp), [CLS 정의](https://web.dev/articles/cls).

event observer는 `durationThreshold:16`으로 수집하고 `interactionId`별 가장 긴 duration을 남긴다. 16ms보다 빠른 이벤트가 생략되고 값이 반올림된다는 한계를 기록한다. 관측된 event 최대값과 상호작용별 분포를 보고한다. 고정 몇 차례의 자동 클릭 결과를 운영 방문 전체의 INP·현장 p75로 표현하지 않는다. 첫 구현에서는 정확한 전체 수명 INP 계산 라이브러리를 추가하지 않는다. [observer threshold](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceObserver/observe), [INP 정의와 비동기 결과 구분](https://web.dev/articles/inp).

시나리오는 로그인 후 홈, 대량 공지 목록과 검색, 전체 승인 부서 필터·깊은 페이지, 대형 결재 상세, 긴 대화 기록 열기와 송수신이다. 실제 role/label로 조작하고 fixture의 대상 ID·건수·상태와 대응하는 API 결과·DOM을 기다린다. 정적인 h1이나 항상 표시되는 버튼만으로 준비 완료를 판정하지 않는다. `requestAnimationFrame`은 후속 프레임 처리 시점의 보조 체크이며 실제 paint 완료를 보장하지 않는다. paint 지표는 브라우저 API가 제공한 entry에서만 산출한다. `networkidle`은 WebSocket·후속 요청을 가진 업무 완료 조건으로 쓰지 않는다. 메신저처럼 연결이 유지되는 화면도 DOM·API의 구체적 완료 신호로 종료한다.

## 6. 원본과 반복 비교

새 실행은 `performance/results/<시각>-<실행ID>/`에 고유 디렉터리를 만들고 기존 결과를 덮어쓰지 않는다.

```text
manifest.json          환경·데이터·조건·코드 식별자
http.jsonl             요청별 시도/상태/본문 검증/시간/바이트/오류
messages.jsonl         발신·echo·수신·deadline·고유 ID·DB 검증
browser.jsonl          페이지·상호작용·observer entry·업무 결과 시간
sql/                   BoundSql, 파라미터, 인덱스, EXPLAIN 원문
summary.json           반복별 분모·평균·p50·p95·최대·실패
README.md              실행 명령·측정 한계·병목 증거·선택한 후속 작업
```

성공 표본의 p50/p95는 정렬한 N개 값에서 `ceil(p×N)`번째를 사용한다. 반복별 값과 범위를 먼저 제시하고, 반복 간 분산이 큰 사례는 안정화 여부를 표시한다. 50회 표본의 p99나 작은 차이를 정밀한 개선율로 강조하지 않는다. 평균·p95를 합치거나 평균내어 전체 분포의 p95인 것처럼 보고하지 않는다.

수정 전후에는 코드 외 조건을 동일하게 유지하고 각 3반복한다. 코드 변경 하나의 원인을 판단하려면 동일 데이터의 기존 SQL과 후보 SQL을 같은 격리 DB에서 교차 측정하며, 전체 요청에서도 반복 확인한다. 결과의 권한·상태·건수·정렬이 달라지는 최적화는 채택하지 않는다. hardware·seed·바이트·캐시·병렬 수가 다르면 비교 표를 나누고 개선율을 계산하지 않는다.

## 7. 현재 코드 리뷰에서 도출한 확인 순서

다음은 정적 코드 근거다. 실행 계획·지연 측정으로 확정한 병목은 아직 없다. 기능 결함은 성능 최적화 전에 회귀 테스트로 재현·정정한다. 이번 검토 이후의 실제 수정·검증 상태는 프로젝트 통합 검증 기록에서 관리한다.

| 우선 | 근거 | 영향·최소 대응 |
|---|---|---|
| 기능 | `approval_mapper.xml`의 완료 목록에는 team 조건, `selectListAllCount`에는 없음 | 부서 필터 목록과 총 건수·페이지 불일치. COUNT에 같은 JOIN·팀 조건을 적용하고 서로 다른 부서 fixture로 검증 |
| 입력 | `searchVO.setPageno`, `ApprovalApiController.searchParams`의 `(page-1)*10` int 계산 | 매우 큰 page에서 음수 offset 가능. 허용 범위 검증 또는 안전한 계산 후 400; HTTP 재현 전에는 500을 관측했다고 주장하지 않음 |
| 측정 | `benchmark.sh`의 `.do` 호출, 임시 표본 삭제, 같은 label 덮어쓰기, timeout 부재·curl 실패 즉시 종료 | API 대체와 원본·실패 분모·제한시간을 새 실행 경계에 반영 |
| 후보 | 완료 목록·COUNT 모두 approval_line 전체 GROUP BY/HAVING | 규모별 actual rows·loops·집계시간 확인. document_status와 결재선 정합성을 보존하는 최소 SQL 단순화부터 비교 |
| 후보 | `ApprovalService.getCounts`가 상태별 COUNT 4회 + 수신 COUNT 1회 | 전체 API 시간과 개별 쿼리 시간을 측정한 뒤 조건부 집계 1회가 유효한지 비교 |
| 후보 | 공지 중요도 정렬, 사원 FIELD 정렬, `%검색어%`·INSTR, OFFSET | 실제 인덱스와 filesort·검색 선택도·깊은 페이지를 확인. EXPLAIN 없이 일괄 인덱스 추가하지 않음 |
| 후보 | `chat_mapper.xml.selectMessages`에 LIMIT 없음, React 전량 표시 | 기록 N에 따라 응답 바이트·DOM·longtask 측정. 필요가 재현되면 messageNo 커서·최근 N개부터 적용하고 복구 범위 테스트 |
| 후보 | `Messenger.jsx` 수신마다 rooms 조회, 수신 방에서는 markRead 후 rooms 재조회 | 메시지당 실제 HTTP·SQL 건수와 UI 시간 확인. 중복 요청을 줄이되 읽음·재접속 복구 유지 |
| 후보 | `ApprovalPages.jsx` Pager와 `NoticePages.jsx`, `UserPages.jsx`가 totalPages만큼 버튼 생성 | 10만 건·페이지당 10이면 최대 1만 버튼. DOM·입력 event 측정 후 현재 페이지 주변 버튼으로 한정 |

InnoDB는 외래키용 인덱스를 만들 수 있으므로 DDL에 별도 `INDEX` 문장이 없다는 사실만으로 인덱스 부재라고 단정하지 않는다. 실제 생성된 인덱스를 `SHOW INDEX`로 확인한다. 방 단위 부모 행 잠금과 결재 문서 잠금은 데이터 무결성을 위한 현재 경계이므로 처리량만을 이유로 제거하지 않는다. 단일 방 편중과 여러 독립 방 부하를 분리해 대기 원인을 확인한다.

## 8. 담당 에이전트와 완료 기준

| 작업 | Agency Agent | 산출물·검증 |
|---|---|---|
| 조건·표본·API/SQL·메시지 계측 | Performance Benchmarker | manifest, 원문·분모·반복별 결과, 측정 한계 |
| 근거가 확인된 SQL 병목 변경 | Database Optimizer | 해당 Mapper·인덱스만 소유, 실제 실행 계획·동일 조건 전후 측정 |
| SQL 변경의 업무 규칙·트랜잭션 보존 | Backend Architect | 필요한 Service·DB 경계만 소유, 실제 MySQL 회귀 |
| 실제 구독·재접속 병목 변경 | Realtime Collaboration Engineer | STOMP·클라이언트 복구 소유, 저장·순서·유실·중복 검증 |
| 브라우저 계측·대량 화면 검증 | Test Automation Engineer | 별도 성능 시나리오, observer·API·DOM 근거 |
| 권한·상태 보존 독립 검토 | Application Security Engineer | 변경 경로의 구체적 증거; 측정 코드가 운영 경로에 유입되지 않는지 확인 |

이 표는 모든 역할을 매번 실행한다는 뜻이 아니다. 주 에이전트가 독립 작업만 실제 커스텀 역할로 위임하고 파일 소유권을 전달한다. 설계 담당은 소유 파일만 편집하며, 주 에이전트가 통합·최종 검증을 순차 실행한다. 동일 `target`에 Maven을 병렬 실행하지 않는다. 역할 본문을 읽은 행위를 실행 검증으로 표현하지 않는다.

설계 완료는 측정 경계·고정 조건·성공/실패 분모·원본·재현 절차·중단 조건이 구체화된 상태다. 단계 5의 실행 완료는 위 원본이 실제 생성되고 같은 조건의 3반복 결과를 재계산할 수 있으며, 느린 사례가 SQL/trace/자원 자료와 연결된 상태다. 원인이 없는 추정, 임의의 처리량 목표, CI 기능 통과만으로 단계 5 완료를 선언하지 않는다.

최적화 채택은 의미 있는 병목의 재현, 가장 작은 수정, 기능·권한·무결성 회귀 통과, 동일 조건의 전후 반복 비교 순서로 진행한다. Redis·외부 브로커·분산 락·Lighthouse/k6 신규 패키지는 현재 도구의 구체적인 한계가 확인될 때 검토한다.
