# 성능 측정 방법

대량 API·SQL, 실제 메신저 전달, 브라우저 로딩·반응성의 반복 측정은 [단계 5 설계](../docs/refactoring/performance-measurement-design.md)를 따른다. `scripts/performance-run.py`가 Docker 없는 새 전용 native 환경을 규모별로 만들고, 데이터 생성·Java HTTP/SQL/STOMP 측정·Playwright 계측·원문 집계를 순서대로 실행한다. 기존 `.local-dev/source`와 기존 복원본은 초기화하거나 재사용하지 않는다.

## 실행

프런트 검사·빌드와 전체 회귀를 먼저 통과한 새 WAR를 사용한다. 동일 `target`을 다른 빌드와 동시에 사용하지 않는다. MySQL 8.4·Java 21·Node와 Chromium이 준비된 프로젝트 루트에서 실행한다.

```bash
python3 scripts/performance-run.py --self-check
node --check frontend/performance/run.mjs

# 포트폴리오 기본 범위: 1천 건·3반복, API 작업자당50회, 메시지 방당100회, 브라우저 조건당10회
python3 scripts/performance-run.py --sizes 1000

# 작은 실행도 반복은3회 이상 유지하며, 실제 표본수는 manifest에 남긴다.
python3 scripts/performance-run.py --sizes 1000 --samples 10 --messages 20 --browser-samples 3
```

다른 MySQL 설치는 `--mysql-bin`, 실행 WAR는 `--war`로 지정한다. 기본 CI에서 `NativePerformanceTest`는 `ERP_PERF_ROOT`가 없으면 생략한다. 직접 외부 URL을 지정하거나 개발 서버에 접속하는 경로는 제공하지 않는다. 전체 CLI가 만든 `.local-dev/performance-<8자리 hex>-<규모>`에서만 실행하며, 완료·실패 시 해당 앱·DB를 종료한다. 기존 결과와 환경은 덮어쓰지 않는다. 실패한 실행의 행·파일은 조사할 수 있도록 남긴다.

사용자 선택에 따라 이번 포트폴리오 실측 범위는 1천 건으로 마쳤으며 기본 규모도 1천 건으로 맞췄다. 완료된 측정 당시 도구3개는 `results/20261022T103945Z-2fe863/1000/measurement-code/`에 해시가 일치하는 사본으로 보존해, 이후 기본값 변경과 측정 원문을 구분한다. 1만·10만 건 옵션은 코드에 남아 있지만 이번 검증 결과에 포함하지 않는다.

## 고정 조건과 실제 측정 경계

seed `20261006`, 규모별 공지 N·결재 N·결재선 3N·메시지 N을 기본 개발 fixture에 추가한다. 사원100명과 별도 관리자1명, 대화방100개, 결재5% 첨부를 만든다. 첫 첨부는1MiB, 나머지는1KiB다. 승인60%·대기20%·진행10%·반려10%와 결재선 상태를 SQL로 대조한다. 새 환경에서만 seed를 허용하고 완료된 fixture를 재생성하지 않는다. `--profile concentrated`는 메시지80%를 한 방에 둔 별도 조건이다.

| 경계 | 원문·검증 |
|---|---|
| API | Java HttpClient 세션·연결 재사용, 병렬1/5, 작업자별 워밍업5회. JSON 건수와 순서 있는 문서·사원·메시지 ID를 독립 SQL 기대값과 비교 |
| SQL | 실제 MyBatis XML의 BoundSql·바인딩 값, 목록과 COUNT, schema·인덱스, EXPLAIN JSON 및 ANALYZE TREE 각3반복. 읽기 SELECT만 실행 |
| 메신저 | 같은 JVM nanoTime, 구독 sync 완료 후 양방향 전송, 발신 echo와 수신자 callback을 분리. 단일방/5개 독립방, DB 저장건수·추가callback·재접속 후 오프라인10개 ID 복구 대조 |
| 브라우저 | production 번들·Chromium·1440×900·단일 worker·재시도0. 새 context와 같은 context 재탐색, API 결과의 대상 DOM 완료 확인 |
| 반응성 | 실제 검색 입력·클릭→rare 검색 건수·문서 제목이 맞는 DOM까지의 action 시간. PerformanceObserver event 원문과 API·React 비동기 완료 시간을 분리 |

로그인 및 MD5→BCrypt 전환은 HTTP 표본 전에 준비한다. API 사례마다 작업자들이 워밍업 종료·측정 종료 barrier를 통과하므로 다른 사례를 섞지 않는다. API 읽기 반복을 먼저 모두 마친 뒤 메시지 쓰기를 실행한다. 메신저의 각 반복·병렬 조건 앞에서 이 도구의 probe 행과 정확한 임시5개 방을 지우고 원래100개 방·N개 메시지를 확인한다. 브라우저 반복 전 seed의 읽음 상태를 복원하고, 대화 기록 warmup이 읽음을 처리한 뒤의 동일 상태에서 기록을 측정한다. 준비·fixture 생성·재설정 시간을 서비스 지연에 포함하지 않는다. Hikari maximum/minimum pool은 성능 환경에서10으로 고정하고 실제 JVM flags·메모리·버전·코드·Mapper·WAR·번들과 새 도구 파일 해시를 보관한다.

## 결과와 실패 해석

각 실행은 `performance/results/<UTC시각>-<ID>/<규모>/`에 다음 파일을 남긴다.

```text
manifest.json               조건·fixture 행 수·native 경로·WAR·Mapper·번들 식별자
java-environment.json       Java/MySQL 버전·예정 API 표본수
browser-environment.json    Node/Playwright/Chromium 버전·viewport·관측 조건
http.jsonl                  워밍업·요청별 상태·검증·시간·바이트·결과 ID·오류
messages.jsonl              echo/수신 지연·DB 무결성·재접속 복구 원문
browser.jsonl               DOM·action시간·observer 원문·DOM 수·API 요청
sql/                       BoundSql·바인딩·EXPLAIN·schema·인덱스
summary.json                반복/사례/병렬별 분모·실패율·nearest-rank 지연분포
java.log, browser-*.log     도구 실행·실패 진단
```

워밍업은 원문에 남기고 요약 표본에서는 제외한다. timeout·오류·잘못된 JSON도 시도 분모에 포함한다. 예정 표본과 실제 시도를 따로 기록하며 중단 후 미시도는 성공으로 세지 않는다. 성공 지연과 실패를 포함한 경과시간 분포를 분리하고 `ceil(p×N)` 순위로 p50·p95를 계산한다. 메시지 `integrity` 행은 건수·중복 점검이므로 지연분포를 만들지 않는다. 원문만 있으면 `summarize(output_path)`로 같은 결과를 다시 계산할 수 있다.

브라우저의 DOM 준비·rAF 시각은 실제 paint 완료를 뜻하지 않는다. 미지원·미관측 LCP/event는 null과 이유를 남긴다. event threshold16ms로 누락되는 빠른 입력이 있고 자동화된 소수 상호작용은 방문 전체 INP나 현장 p75가 아니다. 새 context도 DB·OS 캐시 초기화를 뜻하지 않는다. 관측창은 탐색부터 fixture DOM·다음 rAF까지이며 화면별 길이가 다르므로 CLS를 현장 지표로 확대하지 않는다. HTTP는 응답을 기다리는 closed-loop이며 최대 처리량·최대 동시 사용자 수를 입증하지 않는다. 임의 SLA를 두지 않는다.

실측 결과에서 느린 API와 해당 SQL actual rows·loops·정렬, 브라우저 DOM·longtask를 함께 확인한 뒤 병목을 판정한다. 기능 테스트 시간이나 단일 EXPLAIN의 작은 차이를 개선율로 표현하지 않는다. 실행 결과가 없는 규모는 통과로 기록하지 않는다.

`scripts/benchmark.sh`는 과거 서버 HTML 응답 기록용이다. 현재 React 로그인 HTML에는 hidden CSRF가 없어 실제 격리 요청에서 exit 1로 종료됐다. 또한 정적 HTML 셸만 받아 후속 API·브라우저 렌더·WebSocket 지연을 측정하지 않는다. 과거 결과는 해당 시점·조건의 기록으로 보존하고 현재 실행 가능하거나 사용자 체감 성능을 보장하는 결과로 표현하지 않는다.

[native 개발·복구 검증 기록](../docs/refactoring/review-evidence/native-recovery.json)의 백업·복원 시간은 소량 개발 자료의 기능 점검 1회 값이다. API·메신저 처리량 또는 운영 복구 목표의 근거로 사용하지 않는다.

실제 AI 비교는 `ERP_AI_EVAL_MODELS`로 두 모델 이상을 지정해 `AiModelEvaluationTest`를 실행합니다. 질문별 원문, 실패, 워밍업과 직접 안내를 구분한 응답시간, 동일 프롬프트 해시를 저장합니다. [AI 품질·브라우저 검증 기록](../docs/refactoring/ai-quality-browser-flows.md)에 재현 명령과 측정 범위를 정리했습니다.
