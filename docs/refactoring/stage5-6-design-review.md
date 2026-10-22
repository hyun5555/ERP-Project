# 5·6단계 설계·전체 코드 검토·native 개발 환경 검증

최신 후속 결과(2026-10-22): [데이터·사용성 보완](data-usability-performance.md)으로 첨부·공지 파일명·공백 암호·목록/페이지·메신저 ID/읽음과 화면 대상 혼동을 보완했다. 의존성 High는 0건, 전체 Maven 82개 중 80개 통과·선택 2개 생략, 브라우저 9개 통과다. 성능은 사용자 선택에 따라 [1천 건·3반복](../../performance/results/20261022T103945Z-2fe863/README.md)에서 마무리했다. 아래는 과거 10월 6일 발견·설계 기록이다.

2026-10-22 갱신: 아래 10월 6일 리뷰에서 재현한 세 P1은 [세션·본인 결재·이력 보존 구현](p1-session-approval-history.md)으로 수정했다. 현재 Maven은 71개 중 70개 통과·선택 모델 평가 1개 생략이다. 기존 DB·첨부를 보존한 FK 전환과 새 WAR 반영·독립 복원도 완료했다. 인접한 공백 비밀번호 덮어쓰기 역시 보완했다. 나머지 UI·정합성·성능 항목은 남아 있다. **아래 결과·WAR·백업·미수정 표는 10월 6일 당시 기록이며, 최신 P1 상태는 별도 구현 문서를 기준으로 읽는다.**

2026-10-06. 단계 5의 성능 측정 설계와 단계 6의 실행·복구 설계를 작성하고 현재 프로젝트를 검증했다. 사용자의 최종 선택에 따라 **Docker 없이 앱·MySQL·첨부를 분리한 개발 환경을 실제 구현·초기화·복원했다.** 핵심 회귀·패키징·native 업무·복구는 통과했지만, 재현된 업무·권한 문제와 정적 프런트엔드 문제가 남아 전체 정상 또는 운영 준비 완료로 판정하지 않는다.

## 1. 2026-10-06 실행과 명령

개발 앱: `http://127.0.0.1:59694/ERP`, 개발 fixture 로그인 `admin / 1234`. 외부 인터페이스로 공개하지 않는다. 앱은 실행 WAR이며 DB는 MySQL 8.4.11의 전용 datadir·포트 59693·socket, 첨부는 `.local-dev/source/uploads`다. 기존 Compose·개발 DB·공용 uploads를 사용하지 않는다. Java 21.0.12.1, Python 3.11.15, Node 26.7.0에서 검증했다. MySQL 실행 파일은 Homebrew로 설치했고 시스템 서비스는 시작하지 않았다.

```bash
python3 scripts/dev-environment.py status
python3 scripts/dev-environment.py check
python3 scripts/dev-environment.py stop
python3 scripts/dev-environment.py start
```

새 머신의 첫 초기화는 README의 frontend build→WAR package→`init`→`start` 순서를 따른다. 현재 source에 `init`을 다시 실행하면 기존 데이터 보호를 위해 거부한다. DB·첨부·생성 비밀번호·덤프·로그는 `.local-dev`에 보관하고 Git에서 제외한다. root/앱 비밀번호는 무작위이며 앱은 erp DB의 DML 권한만 가진다. 앱 실행은 `spring.sql.init.mode=never`로 schema·seed 재실행을 막는다.

```bash
# source에 테스트 자료를 만든 뒤 새 독립 복원본에서 검증한다.
python3 scripts/verify-dev-recovery.py

# 백업 경로는 명령의 JSON 출력에서 얻는다.
python3 scripts/dev-environment.py backup
python3 scripts/dev-environment.py --root .local-dev/restore-example restore --backup .local-dev/backups/백업식별자
python3 scripts/dev-environment.py --root .local-dev/restore-example check
python3 scripts/dev-environment.py --root .local-dev/restore-example stop
```

검증 명령은 개발 자료를 작성하므로 실제 업무 데이터를 넣은 환경에서 실행하지 않는다. 직접 하위 새 루트만 허용하며 source/기존 복원본/중첩 경로에 덮어쓰는 복원을 거부한다. 복원 계정은 erp DB에만 DDL 권한을 잠시 받고 import 후 삭제한다. 도구 생성의 신뢰 가능한 백업만 사용한다. checksum은 전송·보관 손상 검출이며 출처 인증을 대신하지 않는다.

최종 백업은 `.local-dev/backups/20261006T034547Z-903e5d`, 검증 복원본은 `.local-dev/restore-isolation-final`이다. 복원본은 검증 후 정상 종료했으며 `--root .local-dev/restore-isolation-final start`로 다시 실행할 수 있다. 원본 개발 앱은 유지했다.

## 2. 실제 검증 결과

| 경계 | 수행 결과 | 근거와 한계 |
|---|---|---|
| 전체 기존 회귀 | Maven 57개 중 56개 통과, 실패·오류 0, 실제 모델 평가 1개 생략 | `./mvnw --batch-mode verify`; native-only 요청 전 Testcontainers 실행. 선택 모델 비교는 앞 단계 별도 기록 |
| 프런트·패키징 | Node 7개, Vite build, 실행 WAR packaging 통과 | WAR manifest의 WarLauncher/ErpApplication과 app.js 포함 확인. native에서는 이 WAR 자체를 java -jar 실행 |
| native 초기화·재기동 | 전용 DB+schema+fixture 생성, 재기동 후 스키마·행·첨부 보존 | 새 datadir, loopback 포트, 절대 uploads. 원본/복원 계정은 각각 별도 생성 |
| 실제 DB 연결·첨부 | 인증 사원 API의 건수=전용 SQL COUNT, 첨부 bytes 일치, 비참여자 403 | 공개 session API 200만으로 DB 정상 판정하지 않음 |
| 백업·새 복원 | 앱 정지→dump+tar+manifest→원래 앱 재기동, 다른 DB에서 스키마·행·파일 일치 | 최종 버전 [복원 결과](review-evidence/native-final-restore.json). 원본 유지, FK orphan 0, 복원 계정 삭제 확인 |
| 복원 후 번호 발급 | 삭제한 자동 번호 이후 서로 다른 새 번호 2개 동시 발급 | [업무·복구 검증](review-evidence/native-recovery.json). 복원본에서만 새 번호 생성, 원본 hash 유지 |
| 환경 보호 | 중첩/부적격 루트·동시 관리·uploads/backup symlink·기존 대상·checksum·tar traversal·mysql 로컬 명령·전역 설정·Java 환경 override 차단 | 전용 fixture로 실행. override 상태에서 소유 DB·포트·첨부 bytes 확인. 악성 SQL의 SET GLOBAL 실행과 archive 동시 교체 공격 자체는 실행하지 않음 |
| native 브라우저 | source와 restore의 UI 로그인→기안→순차 승인→첨부 다운로드 각각 통과 | 기존 Chromium flow 재사용. native WAR에는 JUnit 전용 idle probe가 없어 나머지 만료 4개는 이 환경에서 재실행하지 않음 |
| native WebSocket 로그아웃 | logout 302, HTTP 인증 false, 소켓 close 1008, 이후 메시지 미저장 | [관측 결과](review-evidence/results.json). 초기 취약점 가설을 철회 |
| 성공 비밀번호 변경 UI | API 200·암호 변경 성공 후 reset 예외와 입력 잔존 재현 | 관측 probe의 통과를 UI 정상으로 해석하지 않음. 실제 암호는 검증 후 원래 fixture 값으로 복원 |

최종 WAR SHA-256은 `35525352255d3dde608fb9cd10859715a6ec428b31299269e090391070ee40f0`이다. [환경·코드 manifest](review-evidence/manifest.json)에 revision·버전·파일 해시, [리뷰 관측](review-evidence/results.json)에 실제 HTTP 결과를 보관했다. 임시 JUnit·브라우저 probe는 본래 테스트 파일에서 제거하고 SHA-256으로 원본 복구를 확인했다. 재현용 probe 본문만 evidence 폴더의 `.txt`에 남겼다. 원격 CI는 실행하지 않았다.

복원 검증을 강화하는 과정에서 두 비교 오류를 발견·수정했다. `SHOW CREATE TABLE`은 dump 후 동일한 문자셋의 명시 방식이 달라질 수 있어 실제 information_schema의 컬럼·인덱스·FK·CHECK·engine·collation·AUTO_INCREMENT를 비교한다. AUTO_INCREMENT 조회의 캐시가 실제 증가값보다 뒤처진 사례도 확인해 검증 세션은 `information_schema_stats_expiry=0`으로 최신 값을 읽는다. [MySQL 정보 스키마 설명](https://dev.mysql.com/doc/refman/8.4/en/information-schema-optimization.html).

모든 mysql client 호출은 `--no-login-paths`·소유 socket을 공통 적용하고 stdin import는 `--binary-mode`로 로컬 명령을 차단한다. [client 명령 guard](review-evidence/native-client-guard.json)는 sentinel 미생성과 원본 유지의 실제 결과다. Java 자식은 격리 설정을 덮는 Spring·서버·DB·업로드 및 JVM 주입 환경변수를 제거하고 `ERP_AI_*`는 유지한다. 별도 login-path 파일·잘못된 DB URL·포트·JSON·JVM 환경을 넣어 재기동해도 소유 DB와 원래 첨부를 읽는 것을 확인했다. 사용자의 전역 설정 파일은 변경하지 않았다.

## 3. 2026-10-06 전체 코드 리뷰의 우선 수정 항목

Service·Controller·Mapper/XML·인증·첨부·메신저·AI 경계와 전체 React·API·브라우저 검증 구성을 읽었다. 아래는 native 환경 관리 도구의 문제와 구별되는 **10월 6일 당시 제품 코드의 미수정 항목**이다. 자세한 trigger·위치·최소 수정·회귀 조건은 [보안·정합성 리뷰](code-review-security.md), [프런트엔드 리뷰](code-review-frontend.md)에 있다.

| 우선순위 | 발견 | 확인 수준 | 다음 수정 |
|---|---|---|---|
| P1 | 관리자 강등/퇴직 후 기존 세션의 권한 유지 | 기존 GET/POST 200, 새 세션 403/새 로그인 401 재현 | 공통 인증 경계에서 최신 계정·권한 적용 또는 대상 세션 무효화. HTTP·메신저 함께 확인 |
| P1 | 직접 요청으로 작성자 자신을 결재자로 지정·최종 승인 | 생성 302→본인 승인 302→문서 승인 재현 | 생성/수정 공통 Service에서 허용·재직 결재자 검증 |
| P1 | 사원 삭제가 결재선·완료 문서 이력을 cascade로 삭제 | 첫 결재자 삭제 뒤 둘째 단독 완료, 작성자 삭제 뒤 완료 문서 0 재현 | 참조 사원 하드삭제 제한·비활성화 정책과 이력 보존 |
| P1 후보 | 상세 전환의 이전 응답/표시와 새 승인 대상 혼입 | 정적 경로 확인, 역순 응답 실행 미검증 | 조회 cleanup·ID별 초기화 및 실제 표시/쓰기 ID 검증 |
| P1 후보 | 수정 대상 변경 후 uncontrolled 폼 값 유지 | 정적 경로 확인, 대상 덮어쓰기 실행 미검증 | 대상 ID로 폼 remount, 대상·입력값 일치 회귀 |
| P2 | 성공 암호 변경 뒤 `event.currentTarget.reset()` 오류 | native Chromium에서 실제 재현 | await 전에 form 참조 보관, 성공/오류/초기화/재로그인 확인 |
| P2 | 관리자 수정의 공백 암호를 인코딩 없이 저장 | update 200, 공백 저장, 기존 암호 로그인 401 재현 | 공유 Service에서 빈 암호 변경을 null로 정규화 |
| P2 | 완료 결재 부서 필터의 COUNT 누락 | 목록 1/totalCount 2 재현 | 목록·COUNT 조건 일치 |
| P2 | page 최대 정수의 offset overflow | 사원·공지·결재 API 500 재현 | 공통 페이지 상한/범위 검사 |
| P2 | 첨부 교체/삭제 뒤 파일 잔존, 공지 파일명 바인딩 cleanup | 정적 경로 확인 | 서버 생성 파일명만 신뢰, 성공 commit 후 실제 삭제·고아 점검 |
| P2 | 사원번호 대소문자 alias가 Principal과 달라 실시간 대상 불일치 | 정적 경로 확인 | DB에서 찾은 canonical 사원번호로 방 생성·자기 여부 검사 |
| P2 | 닫힌 메신저 읽음·현황 갱신·로그인 CSRF·키보드 메뉴 문제 | 정적 경로 확인 | 해당 UI 공통 경계의 최소 수정과 실제 브라우저 검증 |

당시 개발 환경의 격리만으로 위 업무 오류가 수정된 것은 아니었다. 이후 세 P1을 별도로 수정했으며, 남은 항목과 운영 검증 범위는 최신 P1 구현 문서에서 구분했다.

## 4. 단계 5의 측정 설계와 남은 범위

[성능 설계](performance-measurement-design.md)는 별도 native 성능 루트, seed 20261006, 1천/1만/10만 규모, 동일 조건 3회, 실제 API·BoundSql/EXPLAIN ANALYZE·수신자 MESSAGE·브라우저 PerformanceObserver 원문을 정의한다. 워밍업·실패 분모·지연·유실·중복·내용 불일치, DOM 업무 완료와 paint 지표를 분리한다. 정적 HTML·기능 테스트 실행시간·소량 복원 시간을 운영 성능으로 확대하지 않는다.

대량 fixture·API/SQL 반복 표본·메신저 전달 p95·브라우저 LCP/반응성 계측은 **설계 완료, 실측 미실행**이다. 기존 benchmark.sh는 React 로그인에서 CSRF 추출 실패(exit 1)도 재현돼 현재 측정 명령에서 제외했다. 실제 성능 수치가 확보되기 전 Redis·외부 브로커·가상 목록·캐시 의존성을 추가하지 않는다. 큰 페이지 버튼·무제한 채팅 이력·다중 COUNT는 측정 후보이며 확정 병목이 아니다.

## 5. 실제 Agency Agents 수행과 완료 기준

| 역할 | 실제 담당 | 소유·수행 |
|---|---|---|
| Performance Benchmarker | `/root/performance_design` | 성능 설계, Mapper/브라우저 경계 검토, native 전환 |
| DevOps Automator | `/root/deployment_design` | 실행·초기화·백업·복구 설계, 실제 CLI와 문서 대조 |
| Application Security Engineer | `/root/security_review` | 전체 백엔드/SQL·새 관리 도구 독립 검토, 오탐 철회·수정 확인 |
| Test Automation Engineer | `/root/browser_e2e` | 전체 프런트·기존 테스트 범위 리뷰, 성능 설계 독립 검토 |
| 주 에이전트 | `/root` | 관리 도구·복구 검증 구현, 실제 회귀·WAR·native DB/API/브라우저 실행, 문서 통합 |

문서 담당은 소유 문서만 수정했고 다른 담당자의 코드를 되돌리지 않았다. 빌드·DB·앱 검증은 주 에이전트가 순차 실행했다. 독립 리뷰에서 발견한 관리 도구의 중첩 루트·symlink·복원 권한·동시 실행·스키마 비교·archive 재open·mysql client·Java 환경변수 문제를 공통 경계에서 보완했다. 설계 문구, 정적 코드 확인, 실행 관측, 실제 수정과 미검증을 구분했다.

10월 6일 정한 후속 순서는 재현 P1 및 대상 혼동 수정→동일 조건 성능 원문 확보→증거가 있는 병목만 개선→별도 운영 설정·복구 환경 검증이었다. 세 P1은 10월 22일 완료했고 대상 혼동과 이후 측정·운영 검증은 남아 있다. Linux/다른 머신, HTTPS·프록시, 장시간 부하, 외부 백업·실제 디스크 장애 복구는 실행하지 않았다.
