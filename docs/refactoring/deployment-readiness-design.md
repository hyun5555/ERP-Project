# 단계 6: 앱·DB·첨부를 분리한 개발 환경과 복원 설계

작성일: 2026-10-06. 사용자의 최신 지시에 따라 Docker Compose 전체 실행 계획을 철회하고, 앱·DB·첨부 저장소를 전용 개발 환경으로 분리하여 초기화·복구를 검증하는 방식으로 바꿨다. `scripts/dev-environment.py`와 `scripts/verify-dev-recovery.py`를 구현하고 주 에이전트가 native MySQL 8.4.11 초기화·WAR 기동·재기동·첨부·동시 번호 발급·백업·새 복원을 실제 검증했다. 이 문서는 해당 구현과 실행 근거를 대조한 기록이며 미수행 범위를 끝에 명시한다.

## 1. 대상과 격리 경계

하나의 전용 루트에 native MySQL 8.4 인스턴스, Java 21 실행 WAR, 업로드 저장소를 서로 다른 경로로 둔다. 기존 개발 서버·DB·uploads는 재사용하지 않는다. 복원도 새로운 전용 루트와 다른 DB·앱 포트·socket을 사용한다. 목표는 같은 개발 머신과 다른 개발 머신에서 초기화·실행·복원을 재현하는 것이다. 운영 서버 공개·HTTPS 프록시·실제 배포는 이번 실행 범위에 포함하지 않는다.

```text
.local-dev/
  source/
    mysql/data/       전용 MySQL datadir
    uploads/          앱이 사용하는 절대 첨부 경로
    run/              PID·socket·생성 설정
    logs/             MySQL·앱 로그
    environment.json  환경 식별자·binary/버전·포트·WAR hash
  restore-<backup>/
    mysql/data/       새 복원 인스턴스의 datadir
    uploads/          비어 있는 새 복원 첨부 경로
    run/
    logs/
```

백업 묶음은 도구가 지정한 별도 디렉터리에 두고 source/restore의 데이터 경로와 겹치지 않게 검사한다. 숨겨진 디렉터리를 사용하는 것만으로 비밀 보호가 된다고 판단하지 않는다. 루트·설정 디렉터리는 사용자만 접근할 수 있게 하고 DB 비밀번호가 담긴 생성 설정·client cnf는 mode 600으로 저장한다. `.local-dev`, 실제 secret·백업·dump·logs를 Git에 넣지 않는다.

| 자원 | source | restore |
|---|---|---|
| MySQL | 전용 datadir, `127.0.0.1` 전용 TCP 포트, 전용 socket·PID | 새 datadir, 다른 loopback 포트·socket·PID |
| 앱 | 실행 가능한 WAR, 별도 앱 포트, source 생성 설정 | 같은 검증 WAR, 다른 앱 포트, restore 생성 설정 |
| 첨부 | source/uploads의 절대 경로 | restore/uploads의 절대 경로 |
| DB 계정 | 무작위 root 비밀번호, DML 전용 앱 계정 | 새 root 비밀번호·앱 계정. source 자격 증명 재사용 안 함 |
| 기록 | source 식별자·생성 시각·schema/WAR hash·프로세스 정보 | backup 식별자·새 환경 정보·복원 검증 결과 |

init은 사용 가능한 loopback 포트 두 개를 선택한다. 기동 때 다른 프로세스와 충돌하면 그 프로세스를 종료하지 않고 오류·로그를 확인한다. 앱 stop은 PID의 command에 해당 환경의 app.properties 경로가 있는지 확인하고, DB stop은 해당 환경 socket과 admin.cnf 인증을 사용한다. 관리 대상은 `.local-dev`의 **직접 하위 비symlink 루트**이며 backups·중첩 경로를 제외한다. environment.json의 root, 주요 경로와 생성 앱 설정이 해당 환경과 일치해야 한다. 기존 자료가 든 init/restore 대상은 거부한다. 관리 명령과 복구 verifier는 같은 `.control.lock`의 flock으로 실행 충돌을 차단한다.

## 2. 최소 관리 도구 계약

주 에이전트가 `scripts/dev-environment.py`를 소유하여 Python 표준 라이브러리와 설치된 Java·MySQL 명령으로 관리한다. 아래 표는 현재 구현과 추가 검증을 구분한다. `--root` 기본값은 `.local-dev/source`, `--war`는 target/erp-1.0.0-SNAPSHOT.war다. `--mysql-bin`은 명시 인자→ERP_MYSQL_BIN→현재 macOS 기본 경로 순이며 다른 환경에서는 설치 경로를 명시한다. Python의 hashlib.file_digest를 사용하므로 Python 3.11 이상이 필요하다.

| 명령 | 행동 | 실패·보존 조건 |
|---|---|---|
| `init` | 새 비어 있는 source 루트, native MySQL 8.4 datadir·무작위 비밀번호·DML계정·fixture/schema 준비 | 비어 있지 않은 대상 거부. 실패 상태·로그 보존 |
| `start` | socket SELECT 1 후 WAR hash 일치 확인·앱 기동·session API 200 대기 | WAR 변경·DB/app 종료·준비 제한시간 실패. 업로드 실쓰기·인증 업무는 별도 verifier |
| `stop` | 소유한 앱 종료 확인 후 소유한 DB 정상 종료 | PID 소유권 불일치면 거부. 기존 개발 프로세스 보존 |
| `status` | 앱 command 확인·DB SELECT 1·접속 URL·DB port·uploads 경로 보고 | 비밀번호 미출력. socket 존재만으로 DB 정상 판정하지 않음 |
| `backup` | 앱이 실행 중이었다면 종료·dump·압축 tar·manifest 작성 후 finally에서 앱 재기동 | 재기동 성공 후 manifest status=complete. 실패 시 로그·미완료 디렉터리 보존 |
| `restore` | backup을 새 restore 루트의 새 DB·uploads로 복원 | source 연결·덮어쓰기·checksum 불일치·비정상 tar 중단 |
| `check` | 익명 session JSON, 전체 schema/row digests, 파일 size/hash, DB 첨부 참조·FK anti-join 읽기 검사 | 익명 인증 조건 불일치도 실패. 번호·인증·쓰기·다운로드는 별도 verifier이며 check는 업무를 쓰지 않음 |

`init`은 빈 전용 datadir에만 개발 seed와 전체 schema를 적용한다. `start`는 schema·fixture를 다시 실행하지 않는다. `restore`는 dump를 사용하며 개발 seed를 다시 넣지 않는다. 앱 실행은 `spring.sql.init.mode=never`로 고정해 기존 application.properties의 always를 덮어쓴다.

생성한 run/app.properties는 server.address=127.0.0.1·앱 포트·전용 JDBC·erp_dev DML계정·init never·uploads 절대 경로를 명시한다. context `/ERP`는 WAR 설정, AI 모델·서버는 기존 설정/AI 환경값을 사용한다. app_environment는 실행 환경의 SPRING_·SERVER_·DB_·ERP_UPLOAD 계열과 JAVA_TOOL_OPTIONS·JDK_JAVA_OPTIONS·_JAVA_OPTIONS를 제거해 다른 DB·포트·업로드·SQL 초기화 설정으로 바뀌지 않게 한다. WAR와 분리한 설정을 `--spring.config.additional-location=file:<root>/run/app.properties`로 읽는다. 로컬 HTTP에서는 Secure 쿠키를 강제하지 않으며 HttpOnly·기존 CSRF·STOMP 보호를 유지한다. 비TLS/public-key 옵션은 loopback 개발용이며 외부 운영 설정으로 재사용하지 않는다.

```bash
# 새 source가 아직 없을 때만 init. ERP_MYSQL_BIN에는 native MySQL 8.4 bin 절대경로 지정
python3 scripts/dev-environment.py --mysql-bin "${ERP_MYSQL_BIN:?}" init
python3 scripts/dev-environment.py start
python3 scripts/dev-environment.py status
python3 scripts/dev-environment.py check
python3 scripts/dev-environment.py backup
# 아래 값은 backup 출력의 실제 경로와 새로운 restore 환경 경로를 지정
python3 scripts/dev-environment.py --root "${ERP_DEV_RESTORE_ROOT:?}" \
  --mysql-bin "${ERP_MYSQL_BIN:?}" restore --backup "${ERP_DEV_BACKUP_DIR:?}"
python3 scripts/dev-environment.py --root "$ERP_DEV_RESTORE_ROOT" check
python3 scripts/dev-environment.py --root "$ERP_DEV_RESTORE_ROOT" stop
python3 scripts/dev-environment.py stop
# 실행 중인 전용 개발 fixture 환경에서만 수행: 테스트 자료를 작성하고 새 복원본 검증
python3 scripts/dev-environment.py start
python3 scripts/verify-dev-recovery.py
```

MySQL client 인자는 공통 mysql_client_args에서 전용 mode 600 cnf·명시 socket·`--no-login-paths`로 만든다. SQL client에는 `--binary-mode`도 적용한다. 전역 login-path의 다른 사용자·호스트·DB가 전용 설정을 대체하지 않도록 하며 init·상태·shutdown·dump·복원에서 같은 경계를 쓴다. 앱은 전용 loopback TCP 포트로 접속한다. root는 초기화·관리·복원에만 쓰고 앱은 업무 DB의 DML만 받는다. 비밀번호는 환경마다 새로 생성하고 argv·보고서·로그에 출력하지 않는다.

## 3. schema·개발 seed·패키징

`document/ERP.sql`에는 DROP·공개 개발 계정·샘플 업무가 있다. 새 전용 init에서만 적용한다. `src/main/resources/schema.sql`의 `user_number_sequence`·채팅 제약도 함께 준비한다. schema.sql만으로 user·결재·공지 기본 테이블을 만들 수 없으므로 파일과 적용 순서를 manifest에 남긴다. 기존 DB 수동 재초기화로 격리 검증을 대신하지 않는다.

source와 restore에서 업무 테이블의 InnoDB engine·utf8mb4 collation·FK·index·timezone을 확인한다. 향후 변경은 번호가 붙은 SQL과 SHA-256으로 이력을 남긴다. 적용 파일이 바뀌면 새 변경 파일을 만든다. DDL은 업무 transaction rollback으로 복구된다고 가정하지 않는다. 자료 삭제·재작성 변경 전에 일치하는 DB+첨부 백업과 새 대상 복원 결과를 확인한다.

기존 Maven WAR packaging·Boot plugin·메인 클래스를 유지한다. JAR 전환·외부 Tomcat 설치 없이 검증 WAR를 `java -jar`로 실행한다. root가 순차적으로 frontend build와 Maven verify를 수행한 뒤 `target/erp-1.0.0-SNAPSHOT.war`의 manifest·업무 클래스·mapper·index.html/app.js/app.css를 검사한다. [Spring 실행 WAR 안내](https://docs.spring.io/spring-boot/how-to/deployment/traditional-deployment.html).

environment.json에는 root·MySQL binary/버전·WAR 경로/hash·DB/app 포트와 init 두 SQL 파일 hash를 저장한다. snapshot은 information_schema의 실제 컬럼·인덱스·FK·CHECK·engine·collation·AUTO_INCREMENT를 정렬해 schema_sha256을 기록한다. 통합 실행 기록에는 revision·Java/Node/Python 버전·명령·결과가 있다. start와 restore는 같은 WAR SHA-256을 요구한다. Python 3.11 이상·Java 21·native MySQL 8.4의 실제 binary를 확인하며, 이번 검증은 macOS arm64다. AI는 기존 Ollama를 선택하고 장애가 다른 업무 기동을 막지 않는다.

## 4. 기동·종료·상태 검증

현재 start_db는 socket SELECT 1과 최대 45초 준비 대기, start는 WAR hash·앱 PID·최대 60초 session API 대기를 수행한다. 공개 session API는 DB를 검사하지 않으므로 그 200만으로 업무 준비 완료를 주장하지 않는다. check의 실제 table/파일 읽기와 별도 verifier의 DML계정·로그인·업무·실쓰기 결과를 함께 확인한다.

새 health 서버나 Actuator 의존성은 요구하지 않는다. `check`가 기존 API와 별도 SQL로 상태를 판단한다. 재기동 시 재로그인과 WebSocket 재접속이 필요하다. 메신저는 단일 app의 in-memory SimpleBroker를 유지한다.

정상 종료는 앱 종료 신호→진행 업무 제한 시간 대기→실제 종료 확인→MySQL 정상 종료 순서다. 강제 종료를 정상으로 기록하지 않는다. 기존 MySQL 서비스의 관리 명령·공유 PID·공용 socket으로 stop하지 않는다. source·backup 이후·restore 기동에서 실제 DB/WAR/uploads 경로를 확인한다. 상태·오류에는 비밀이 아닌 진단만 포함하고 client 설정 내용은 출력하지 않는다.

## 5. DB·첨부 같은 시점 백업

첨부 저장은 DB transaction에 포함되지 않는다. file write와 DB insert 사이 종료는 고아 파일을 남길 수 있다. 실행 중 dump와 uploads를 따로 복사하면 다른 시점의 자료가 된다. 순서는 **백업 parent symlink/경로 검사→소유한 app 종료 확인→외부 writer·DDL 없음 확인→DB dump+uploads+manifest 생성→원래 app 재기동**이다. parent가 잘못됐으면 app을 멈추기 전에 차단한다. DB는 dump 동안 실행한다.

dump는 `--single-transaction --no-tablespaces --set-gtid-purged=OFF --databases erp`를 사용한다. source와 restore는 서로 다른 전용 mysqld의 동일 DB 이름 erp를 사용하므로 USE erp가 source 인스턴스로 연결을 바꾸지 않는다. 복원은 새 socket의 일회 erp_restore 계정으로 import하고 해당 DB에만 DDL 권한을 부여한 뒤 계정·설정 파일을 제거한다. 공통 SQL client에는 `--binary-mode`와 명시 socket을 적용한다. dump 중 DDL·파일 수정을 하지 않으며 snapshot은 InnoDB·utf8mb4 테이블을 검사한다. DB 옵션이 첨부 일관성까지 제공하지 않으므로 앱 정지를 유지한다. [MySQL mysqldump 기준](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html).

| 구성 | 필수 정보 |
|---|---|
| database.sql | erp 전체 schema/data와 user_number_sequence 포함, payload SHA-256 |
| uploads.tar.gz | snapshot에 기록한 일반 파일만 압축, payload SHA-256, 각 파일 size/SHA-256 |
| manifest.json | 시작/완료 UTC·status·app_restarted·source_root·init schema hashes·MySQL 버전·WAR SHA-256·schema/row digests·파일·참조·FK orphan·payload hashes |
| 완료 판단 | export 직후 status=exported, 필요했던 source 앱 재기동 성공 후 status=complete. restore는 complete만 허용 |

snapshot은 DB 참조 파일 누락·upload symlink·FK orphan을 거부하고 일반 파일·schema/row digests를 기록한다. DB 미참조 파일도 보관하며 자동 삭제하지 않는다. manifest가 payload 두 파일 hash와 완료 상태를 담는다. 별도 COMPLETE 파일은 필요하지 않으며 manifest 자체 서명은 제공하지 않는다. checksum은 손상 검출이므로 도구가 생성한 신뢰 가능한 백업만 사용한다. umask 077·manifest mode 600을 적용한다. 실패한 묶음을 사용하지 않고 source 재기동을 확인한다.

schema 비교는 SHOW CREATE TABLE 원문 hash 대신 native metadata를 사용한다. dump 후 같은 charset의 명시 방식이 달라진 사례를 확인했기 때문이다. AUTO_INCREMENT metadata의 지연 캐시도 발견해 각 검증 SQL 세션은 information_schema_stats_expiry=0으로 최신 값을 읽는다. 이 보완 후 실제 전체 schema·행·파일이 일치했다.

외부 보관·암호화·보존 횟수·허용 손실/복구 시간은 사용자가 결정할 별도 항목이다. 같은 머신 디스크의 재현 검증을 실제 서버 디스크 장애 대비 완료로 확대하지 않는다. 이번 도구에 자동 보존 삭제를 넣지 않는다.

## 6. 새 인스턴스 복원과 검증

restore는 backup에 대응하는 새 `.local-dev/restore-<backup>`만 만든다. 루트/datadir/uploads가 기존에 있거나 source와 경로·포트·socket이 겹치면 중단한다. 새 계정·비밀번호를 만든 뒤 새 DB에 import한다. 원본 source나 기존 개발 DB에 import하지 않는다.

restore는 완료 상태·payload hash·같은 WAR·안전한 tar members를 **초기화 전에** 확인한다. 이후 seed=False 새 인스턴스 초기화→일회 DB 제한 계정으로 import→tar 재open 때 member 이름/size/일반 파일/안전 경로 재검증→추출→전체 snapshot 일치→앱 기동 순서다. 절대 경로·..·symlink/hardlink/special entry를 거부한다. archive가 두 검사 사이에 공격적으로 교체되는 실행 자체는 이번에 재현하지 않았다. SQL은 새 erp DB에만 적용하며 원본 이름을 치환하거나 정상 USE erp를 거부하지 않는다.

| 검사 | 통과 조건 |
|---|---|
| schema·행 수 | schema native metadata·전체 row count·정렬된 row SHA-256 실제 일치. 컬럼·index·FK·CHECK·engine/collation·최신 AUTO_INCREMENT 포함 |
| DB 관계 | approval→file/line/user, notice→team/user, chat→room/sender 미연결 0건. import가 FOREIGN_KEY_CHECKS를 해제해도 anti-join으로 별도 확인 |
| 첨부 | 참조 파일 전부 존재, size·SHA-256 동일, 권한 있는 다운로드 bytes 동일, 권한 없는 결재 파일 403 |
| 사원번호 | sequence 행 일치, 삭제한 자동 번호 이후 서로 다른 번호 2개 동시 발급 실제 확인. 999 경계는 기존 격리 회귀 근거이며 native 복원 검증에서 재실행하지 않음 |
| cascade | 복원본의 테스트 문서·공지·채팅 child cleanup. 사원 하드삭제로 결재 이력을 지워도 된다는 업무 정책의 승인이 아님 |
| 웹 업무 | native 로그인·인증 API·작성·첨부·번호 발급, source/restore Chromium 작성→순차 승인→download 통과. 전체 만료/STOMP/SSE 경로는 기존 회귀 범위와 구분 |
| 재기동 | 전용 DB/app 재기동 후 행·파일 hash 유지, 재로그인·다운로드 성공 |

check/restore snapshot 비교 자체는 읽기 전용이다. 별도 native verifier는 최초 일치 확인 후 전용 테스트 자료만 작성하고 바뀐 행·번호를 기록한다. 복원 시작부터 로그인·다운로드 완료까지 실제 시간을 남긴다. import exit 0·PID·공개 session 200만으로 업무 복원 성공을 판정하지 않는다.

코드만 되돌릴 때 schema 호환성을 확인하고 이전 WAR를 같은 DB·uploads에 연결한다. DB·첨부도 되돌려야 하면 같은 backup에서 복원한 새 환경으로 바꾸며 backup 이후 데이터 손실 가능성을 기록한다. 다른 시점의 DB·uploads를 섞지 않는다. 원본과 복원본은 검증 완료 전까지 유지하고 자동 삭제하지 않는다.

## 7. 선행 보안·업무 수정

독립 보안 리뷰와 주 에이전트의 격리 probe에서 **P1 세 가지**가 재현됐다. 환경 분리·복원으로 해결되지 않으며 실제 운영 공개 전에는 수정과 해당 경로의 회귀 검증이 필요하다. 근거·미검증 범위는 [보안 코드 리뷰](code-review-security.md)에 있다.

| 재현된 P1 | 수정·검증 |
|---|---|
| 관리자 권한·재직 변경 후 기존 HTTP session cache 사용 | 최신 계정·권한 적용 또는 대상 세션 무효화. 두 로그인 세션으로 회수·퇴사 후 실제 요청 차단 확인 |
| 작성자·결재자 user 하드삭제 FK cascade | 문서·결재선·승인 이력·순서 삭제 정책 수정. 비활성화/삭제 이후 기록·처리 규칙 보존 |
| Service 결재자 후보 검증 부재와 자가 결재 | 생성·수정 공통 Service의 허용 후보 검증. 직접 요청 자가/부적격 대상 거부와 transaction 원자성 |

초기 “로그아웃해도 기존 WebSocket이 유지된다” 가설은 철회했다. 주 에이전트 probe는 Connection closed였고 Tomcat 11.0.24는 sessionDestroyed에서 인증 소켓을 정책 위반 1008로 닫는다. 새 로그아웃 소켓 관리 코드를 요구하지 않는다. idle expiry 타이밍과 DB 권한·퇴사 변경 반영은 다른 조건이며 로그아웃 관측으로 함께 검증됐다고 표현하지 않는다.

## 8. 에이전트 소유권과 완료 조건

| 실제 Agency Agent/담당 | 소유 범위 | 검증 근거 |
|---|---|---|
| 주 에이전트 | scripts/dev-environment.py·verify-dev-recovery.py, ignore·실행 안내 통합, build·DB·앱 실행권 | source init/restart, 실제 backup/restore/check·번호 발급, 기존 환경 보존 |
| DevOps Automator | 이 설계 문서, native 기동·권한·초기화·복원 읽기 전용 검토 | 명령·경로·구현 일치, 비밀·대상 혼동·실패 보존 검토 |
| Performance Benchmarker | 성능 설계·실제 병목 근거 읽기 전용 검토 | API·SQL·STOMP·브라우저 동일 조건·성공/실패 기준 |
| Application Security Engineer | 인증·업무·파일 경계 독립 리뷰 문서 | 확정 P1과 P2, 수정/재현/철회 구분 |
| Test Automation Engineer | 전체 화면·프론트 리뷰 및 브라우저 probe | 호출자·API 계약·반응성·오류 처리 근거 |

담당자는 실제 설치된 커스텀 역할로 실행한다. 파일은 한 소유자만 수정하고 다른 변경을 되돌리지 않는다. 이 설계 담당은 문서 밖 소스 수정·빌드·DB 실행을 하지 않는다. 주 에이전트가 같은 target·source DB의 실행을 순차 수행하고 전체 회귀·새 환경을 통합한다. 역할 문구·문서 작성은 구현·검증 통과의 증거가 아니다.

구현 파일은 관리 도구와 의미 있는 실행 회귀인 verify-dev-recovery.py, 기존 ignore·README/검증 기록의 필요한 변경이다. WAR·schema·fixture·회귀 테스트를 재사용한다. 별도의 Dockerfile·Compose·프록시·복원 앱 서비스를 현재 대상에 추가하지 않았다. 최종 옵션·예제는 실제 도구와 대조했다.

개발 환경의 source 초기화·실행·정상 재기동·같은 시점 backup·새 restore 일치·실제 첨부·번호 검증은 완료했다. 외부 운영 배포 완료와 구분하며 결과가 없는 항목은 미검증으로 남긴다.

### 실제 실행 근거와 남은 범위

[native 업무·복구 검증](review-evidence/native-recovery.json)은 재기동 후 schema/행/첨부 보존, 인증 API·download bytes·비참여자 403, 기존 대상·checksum/tar traversal/루트 symlink/동시 관리 차단, 복원 후 동시 번호 2개와 source hash 보존을 기록한다. 최종 verifier는 잘못된 전역 MySQL login-path·Java 환경 override를 제공한 조건에서도 실제 source DB·소유 앱 포트·첨부 bytes가 유지됨을 확인했다. backup parent symlink는 app stop 전에 차단되어 외부 출력이 없고 앱 PID가 유지됐다. 최신 소량 일회 backup·restore-download 시간은 JSON 원문의 backup_seconds·restore_seconds_through_download를 기준으로 한다. 반복 성능이나 복구 SLA가 아니다.

[최종 복원 결과](review-evidence/native-final-restore.json)는 새 pristine restore-isolation-final에서 최신 metadata로 schema·행·파일 일치, source 미변경, 일회 import 계정 제거, 검증 후 정상 종료를 확인한다. binary mode·SQL client 로컬 명령 차단·전역 client/앱 override 배제·backup symlink 차단도 완료 결과에 있다. 복원 시간은 해당 원문의 seconds를 기준으로 한다. [통합 기록](stage5-6-design-review.md)에는 WAR hash·버전·회귀·native 브라우저·오탐 철회와 남은 코드 문제가 있다. source 앱은 127.0.0.1:59694, DB는 전용 59693이다. 복원본은 다른 포트이며 최종 검증 후 정지했다.

대량 데이터 성능 반복 측정, 다른 호스트/Linux 재현, 외부 HTTPS·프록시·운영 배포, 외부 백업·디스크 장애 복구는 미수행이다. native verifier는 source fixture를 작성하므로 실제 업무 데이터 환경에서 실행하지 않는다. 관측한 SQL client 명령 차단을 백업 출처 인증·모든 악성 SQL 방어로 확대하지 않는다.

## 9. 현재 코드의 실행·복원 격차

| 우선순위 | 근거 | 영향·최소 조치 |
|---|---|---|
| 높음 | application.properties:6–10,16 | 개발 fallback·always DDL·상대 uploads. 전용 생성 설정·init never와 실제 연결 검사 |
| 높음 | document/ERP.sql:2–14,118–144, schema.sql | 기존 환경에 DROP/seed 실행 시 손실, sequence 누락 시 번호 재사용. 새 init만 적용·restore는 완전 dump |
| 중간 | DashboardApiController.java:31–36 | 공개 session API는 DB 장애에도 응답 가능. SQL·인증 API·파일 검사 병행 |
| 중간 | ApprovalService.java:88–100, ApprovalController.java:56–59 | 첨부 교체·삭제·사원 cascade 후 실제 파일이 남음. 고아 파일 기록·자동삭제 금지 |
| 중간 | ApprovalController.java:106,131,153–159, NoticeController.java:88,112–120 | file write→insert 사이 종료·IOException으로 고아/부분 파일. 앱 종료 후 백업·reconciliation |
| 중간 | .github/workflows/ci.yml:31–33, pom.xml:66–74 | 기존 CI test만으로 WAR·native 기동·복원 보장 불가. verify/WAR 검사와 native 실행 결과 분리 |
| 중간 | scripts/benchmark.sh:18–25,78–81 | React HTML hidden CSRF 가정·셸 URL 측정. 단계5 실제 API/SQL/브라우저와 구분 |
| 낮음 | frontend/vite.config.js:6,12–17 | context 변경·고정 assets 장기 cache는 불일치 가능. 전용 실행도 /ERP 유지 |

정적 격차와 실제 실행 결과를 섞지 않는다. source line은 공유 트리의 검토 시점 기준이며 최종 통합 때 최신 구현과 대조한다. native 검증으로 P1/P2 업무 문제가 수정됐다고 표현하지 않는다.
