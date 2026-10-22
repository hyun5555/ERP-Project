# P1 설계·구현: 세션 권한 회수, 본인 결재 차단, 사원 이력 보존

후속 상태(2026-10-22): 이후 의존성·데이터·사용성 보완과 포트폴리오용 1천 건 측정을 완료했다. 최신 개발 WAR·검증 결과는 [후속 구현 기록](data-usability-performance.md)을 따른다. 아래 WAR·백업은 P1 반영 당시 기록이다.

2026-10-21 시작, 2026-10-22 구현·검증 완료. 사용자 요청의 세 가지 P1을 현재 Spring Security·Servlet 세션·MyBatis·MySQL 구조에서 보완했다. 기존 native 개발 DB와 첨부를 보존한 채 새 WAR와 FK 변경을 반영했다. 실행 근거는 [P1 결과 JSON](review-evidence/p1-20261022.json)에 있다.

## 1. 업무 규칙

| 경계 | 확정한 동작 |
|---|---|
| 관리자 강등 | 변경 성공 후 해당 사원의 기존 로그인 세션을 모두 종료한다. 재로그인한 재직자는 일반 사용자 권한을 받는다. |
| 퇴직·삭제 | 변경 성공 후 해당 사원의 기존 세션과 연결된 인증 WebSocket을 회수한다. 퇴직 계정의 새 로그인은 거부한다. |
| 변경 실패 | 트랜잭션이 롤백되면 세션을 회수하지 않는다. 다른 사원의 세션도 유지한다. |
| 로그인 경합 | 로그인 완료 시 현재 계정·재직·권한을 다시 확인하고 계정 변경과 등록을 DB 행 경계에서 조정한다. |
| 결재자 등록 | 생성·수정 공통 Service에서 재직 중인 비관리자만 허용한다. 본인·미존재·퇴직·관리자·중복을 거부하고 DB의 정식 사원번호를 사용한다. |
| 기존 본인 결재선 | 새 등록만 제한하지 않고 이미 저장된 본인 결재선의 승인·반려도 차단한다. |
| 이력 있는 사원 삭제 | 단일·일괄 삭제를 409로 거절한다. 사원을 퇴직 상태로 갱신하여 계정과 기존 업무 이력을 보존한다. |
| 이력 없는 사원 삭제 | 삭제를 허용한다. 일괄 대상 중 한 명이라도 이력이 있으면 전체 요청을 거절한다. |

사원 퇴직으로 기존 결재선을 자동 삭제하거나 건너뛰지 않는다. 승인 내역·결재 순서·대화 내용을 그대로 남긴다. 업무 인계·결재선 대리 처리 기능은 이 변경에 포함하지 않는다.

## 2. 호출·트랜잭션 경계

로그인은 현재 DB 계정 확인 → 세션 등록으로 연결한다. 사원 변경·삭제는 현재 관리자 DB 상태와 대상 계정을 확인 → 변경 → 성공 커밋 → 해당 사원의 세션 무효화 순서다. `SessionRevocationService`가 Servlet 세션 등록과 커밋 이후 회수를 담당한다. 현재 Tomcat은 세션 무효화 시 연결된 인증 WebSocket을 close 1008로 종료한다. 요청에 남은 관리자 VO만으로 User·Notice Service의 관리자 변경 권한을 인정하지 않는다.

같은 쿠키로 A에서 B로 다시 로그인하면 등록 객체도 교체한다. 회수할 A의 등록 객체를 캡처한 뒤 B로 로그인하고 커밋하는 경합에서도 객체 동일성을 다시 확인해 B의 세션을 종료하지 않는다. 사용자 행의 정렬된 잠금과 current read로 로그인·변경 순서를 조정하며, 이전 MyBatis 캐시나 REPEATABLE READ 스냅샷의 관리자 권한을 재사용하지 않는다.

결재 생성·수정은 작성자와 결재 대상의 정식 사원번호·현재 상태를 확인하고 필요한 사용자 행을 일정한 순서로 잠근 뒤 문서·결재선·첨부 메타데이터를 쓴다. 부적격 대상이 있으면 쓰기 전에 거절한다. 수정·결재 처리의 기존 문서 잠금과 순서·종결 상태 규칙은 유지한다.

사원 삭제는 공통 Service에서 대상 사용자 행과 업무 참조를 확인한다. MySQL FK의 RESTRICT가 직접 삭제와 동시 참조 생성에도 이력을 보호한다. 참조 범위는 결재 작성자·결재선 대상·공지 작성자·대화방 양쪽 사원·메시지 발신자다. 문서를 정상 삭제할 때 문서의 파일·결재선 자식을 함께 정리하는 기존 관계는 유지한다.

## 3. 기존 DB 전환

새 개발 fixture와 schema는 사용자 참조 FK에 RESTRICT를 사용한다. 기존 DB에는 `document/migrations/20261021-preserve-user-history.sql`의 비파괴 ALTER를 별도로 적용한다. 앱 시작 시 migration이나 개발 seed를 자동 실행하지 않는다.

기존 환경 전환은 앱 쓰기 중지 → 이전 WAR 보관 및 DB·첨부 백업 → 사용자 참조 FK migration → 행·참조·FK 규칙 확인 → 새 앱 실행 → 새 백업의 독립 복원 검증 순서로 수행했다. DROP/seed 재실행으로 migration을 대신하지 않았다.

MySQL 8.4에서 FK 검사를 켠 채 같은 이름의 FK를 한 ALTER로 DROP·ADD하면 오류 1826이 발생해 실행 중 수정했다. 최종 SQL은 테이블마다 임시 RESTRICT FK 추가 → 기존 FK 제거 → 원래 이름으로 RESTRICT 추가 → 임시 FK 제거를 별도 ALTER로 실행한다. 총 20개 ALTER로 사용자 참조 6개를 바꾸고, FK 검사를 해제하지 않는다. 완료된 migration의 재실행도 검증했다. 근거: [MySQL FK DDL 제약](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html), [온라인 DDL 동작](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html).

MySQL DDL은 전체 파일 단위로 원자적이지 않다. 중간에 중단되면 쓰기를 계속 중지하고 `SHOW CREATE TABLE`로 완료한 단계를 확인한 후 다음 단계부터 진행한다. 원래 이름의 FK가 준비되기 전에 임시 guard를 제거하지 않는다. 중간 중단 복구와 전체 롤백의 실행 연습은 이번 범위에서 수행하지 않았다.

## 4. 검증 범위

실제 Testcontainers MySQL과 HTTP·WebSocket으로 다음을 확인한다. 기존 전체 회귀와 새 P1 회귀를 함께 실행하고, 별도 검토 담당이 호출자·실패 조건·데이터 보존을 대조한다.

1. 두 기존 로그인 세션의 강등·퇴직·삭제 후 접근 차단, 재로그인 권한, 무관한 사용자·롤백 세션 유지.
2. 기존 인증 WebSocket의 실제 종료와 다른 사원 통신 유지.
3. 부적격 결재자의 생성·수정 거부, 정식 번호 처리, 이전 문서·결재선·첨부 메타데이터 유지, 기존 본인 결재선 처리 거부.
4. 이력 있는 사원의 단일·일괄 삭제 409, 직접 SQL 삭제의 FK 거부, 퇴직 처리 후 업무 원문 보존.
5. 기존 FK에서 migration을 적용해도 데이터가 유지되고 재적용 가능한지 확인.
6. 같은 쿠키의 계정 전환과 회수 커밋 경합, 오래된 트랜잭션 스냅샷·Mapper 캐시의 권한 재사용 차단.

## 5. 구현·실행 상태

| 담당 | 실제 Agency Agent | 소유·검토 범위 |
|---|---|---|
| `/root/p1_backend` | Backend Architect | 세션 등록·회수, Security/User/Notice/Approval/Chat Service, Mapper·schema·migration |
| `/root/p1_regression` | API Tester | CoreFlowIntegrationTest·MessengerWebSocketIntegrationTest의 P1 회귀 |
| `/root/p1_review` | Application Security Engineer | 변경 호출자·실패 조건·로그인 경합·데이터 보존 독립 검토 |
| `/root` | 주 에이전트 | 통합, 순차 빌드·DB·native 갱신·복원·문서 대조 |

각 담당은 다른 담당자의 변경을 유지했다. 독립 검토에서 찾은 같은 쿠키 재로그인 경합과 사원번호 발급의 잠금 순서 문제를 보완한 뒤 전체 회귀를 실행했다. 빈 비밀번호 수정이 기존 해시를 덮는 인접 결함도 Service에서 blank를 null로 정규화해 보완했다.

| 실행 | 결과 |
|---|---|
| `npm --prefix frontend ci` | 설치 성공. 기존 의존성 High 2건은 별도 후속 항목 |
| `npm --prefix frontend test` | Node 7개 통과 |
| `npm --prefix frontend run build` | Vite 빌드 성공 |
| `./mvnw --batch-mode test` | 총 71개 중 70개 통과, 실제 모델 평가 1개 생략, 실패·오류 0 |
| 업무·실제 WebSocket | 각각 35개·22개 통과. 이번 P1 회귀 14개 추가 |
| 브라우저·모델 HTTP | JUnit 브라우저 실행 1개 안의 Playwright 5개 흐름 통과, 모델 HTTP 12개 통과 |
| FK migration 단독·전체 실행 | 이전 FK 전환·완료 후 재적용·원문 행 보존 통과 |
| `./mvnw --batch-mode -DskipTests package` | 실행 WAR 패키징 성공 |

Maven 로그는 `/private/tmp/erp-p1-20261022-final.log`, 패키징 로그는 `/private/tmp/erp-p1-20261022-package.log`다. 임시 로그의 보존은 보장하지 않으므로 결과 수와 소스·산출물 해시는 결과 JSON에도 남겼다. 테스트 실행 시간을 성능 개선 수치로 사용하지 않는다.

## 6. native 개발 환경 반영

현재 개발 앱은 `http://127.0.0.1:59694/ERP`, DB 포트는 59693, 첨부 경로는 `.local-dev/source/uploads`다. 새 WAR SHA-256은 `a42e75c42efa197992d949888385092b5918a7cb9ceafefb3e77bc5ba0d94c28`이며 `.local-dev/artifacts/<SHA-256>/erp.war`에 보관했다. 기존 환경 설정의 WAR 경로·해시만 새 파일로 바꿨다. 실제 실행 프로세스의 WAR 경로, 클래스·Mapper 포함 여부와 파일 해시도 확인했다.

전환 전 백업은 `.local-dev/backups/20261021T220935Z-640a22`, 전환 후 백업은 `.local-dev/backups/20261021T221121Z-607276`이다. 이름의 날짜는 UTC이며 사용자 기준 실행일은 10월 22일이다. 원래 9개 테이블의 행 수·행 해시, 첨부 6개 bytes·참조가 모두 유지됐다. 사용자 FK 6개는 RESTRICT, FK 검사 활성, orphan은 0이다. 실제 인증 사원 API의 건수와 전용 DB의 COUNT도 일치했다.

전환 후 백업은 새 `.local-dev/p1-restore-20261022`에 복원해 schema·행·첨부 일치와 원본 유지까지 확인했다. 복원본 앱·DB는 검증 후 종료하고 source 앱·DB는 실행 중이다. 다음 명령은 이 환경의 상태를 확인하며 초기화하지 않는다.

```bash
python3 scripts/dev-environment.py status
python3 scripts/dev-environment.py check
```

## 7. 남은 한계

세션 등록부는 현재 개인 개발의 단일 앱 인스턴스에 있다. 계정 변경은 공통 UserService 경로를 사용해야 하며 외부 SQL로 바꾼 계정 상태의 자동 전파는 구현하지 않았다. 다중 앱 인스턴스 운영은 별도 설계가 필요하다.

기존 본인 결재선과 퇴직자의 과거 결재선은 이력으로 남기고 실제 처리를 차단한다. 대리 결재·업무 인계 정책은 별도 기능이다. 이번에 실제 AI 모델 비교·원격 CI·다른 머신·운영 HTTPS를 다시 실행하지 않았다.

나머지 화면 대상 혼동, 암호 변경 후 폼 초기화, 첨부 정리, 목록·페이지 정합성, 대량 성능 측정은 후속 범위다. 설치 감사에서 확인한 `vite`, `source-map-js`의 High 2건도 이번 의존성 변경 없이 기록했다. 세 P1 완료를 전체 제품의 운영 준비 완료로 확대하지 않는다.
