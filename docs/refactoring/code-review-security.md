# 백엔드 보안·업무 정합성 코드 리뷰

최신 후속 결과(2026-10-22): SEC-05~10의 암호·첨부/공지 파일명·메신저 정식 번호·COUNT·offset 문제도 수정하고 실제 MySQL/HTTP/WebSocket 회귀를 통과했다. [후속 설계·검증 결과](data-usability-performance.md)에 구현·독립 검토·첨부 I/O 잔존 한계를 기록했다. 아래 발견 설명과 줄 번호는 과거 상태다.

2026-10-22 후속 상태: SEC-01의 강등·퇴직 세션 회수, SEC-03 본인 결재, SEC-04 사용자 삭제 cascade를 수정하고 실제 HTTP·WebSocket·MySQL 회귀로 검증했다. SEC-05의 blank 비밀번호도 null로 정규화했다. [P1 설계·구현](p1-session-approval-history.md)과 [최신 실행 결과](review-evidence/p1-20261022.json)를 참조한다. **아래 코드 위치·재현·표는 10월 6일 당시 발견 기록이다.** 부서 변경에 따른 AI 문맥 갱신 등 요청한 세 P1 밖의 경계까지 완료로 판정하지 않는다.

검토일: 2026-10-06. 현재 공유 작업 트리의 Java Controller·Service, MyBatis Mapper, 인증·WebSocket 설정, DB 제약과 파일 저장 경로를 추적했다. 이 검토자는 애플리케이션·테스트·DB를 실행하지 않았고 소스 코드를 수정하지 않았다. 주 에이전트가 별도로 격리 probe를 실행했으며 그 결과를 아래에서 구분했다. 별도 실행 결과가 없는 항목은 정적 코드 확인 결과다.

실행 관측의 원본은 [review-evidence/results.json](review-evidence/results.json)이다. backend 결함 관측과 Maven verify는 Docker 없는 native 실행 지시 이전의 Testcontainers 격리 실행이었다. 이후의 환경 분리·백업/복원과 WebSocket 로그아웃은 native 전용 환경에서 별도로 수행했다. 과거 관측 성공은 취약점 수정의 증거가 아니다.

P1은 권한 회수 또는 결재의 승인·기록 보존을 깨뜨리는 배포 전 수정 항목이다. P2는 특정 입력이나 정상 파일 작업에서 발생하는 정합성·입력 검증 결함이다. 조직별 승인 절차, 임의의 SLA, 새 인프라를 요구하지 않는다.

| ID | 우선순위 | 확인된 경로 | 2026-10-06 검증 상태 |
|---|---|---|---|
| SEC-01 | P1 | 권한·근무상태 변경 후 기존 HTTP 세션 권한 유지 | 주 에이전트 격리 실행 재현 |
| SEC-03 | P1 | 결재 대상 후보 제한을 직접 요청으로 우회 | 자기 결재를 주 에이전트 격리 실행 재현 |
| SEC-04 | P1 | 사원 삭제의 CASCADE가 결재선·완료 기록 삭제 | 주 에이전트 격리 실행 재현 |
| SEC-05 | P2 | 공백 비밀번호 수정이 기존 해시를 공백으로 덮어씀 | 주 에이전트 격리 실행 재현 |
| SEC-06 | P2 | 결재 첨부 교체·삭제 후 실제 파일 잔존 | 정적 확인 |
| SEC-07 | P2 | 공지 파일명 바인딩이 실패 요청에서 기존 파일 삭제 | 정적 확인, 업로드 내부 파일명 사전 지식 필요 |
| SEC-08 | P2 | 대화 상대 번호의 대소문자 차이로 실시간 배달 누락 | 정적 확인 |
| SEC-09 | P2 | 완료 결재 부서 필터가 개수 조회에서 누락 | 주 에이전트 격리 실행 재현 |
| SEC-10 | P2 | 큰 page 입력의 정수 오버플로로 잘못된 LIMIT 전달 | 주 에이전트 격리 실행 재현 |

SEC-02의 초기 WebSocket 로그아웃 유지 가설은 실행 결과와 Tomcat 내장 구현을 확인한 후 철회했다. 아래에 그 근거를 보존한다.

## SEC-01 — 권한 회수·퇴사 처리 이후 기존 HTTP 세션이 권한을 유지한다

근거: `src/main/java/com/erp/service/LoginService.java:31`은 로그인 때 DB 상태로 ADMIN/USER와 disabled를 구성한다. `src/main/java/com/erp/config/SecurityConfig.java:66`은 같은 시점의 `userVO`를 `loginUser` 세션 속성으로 저장한다. 이후 관리자 사원 수정은 `src/main/java/com/erp/service/UserService.java:59`와 `src/main/resources/mapper/user_mapper.xml:106`에서 DB만 변경한다. HTTP 관리 경로는 `SecurityConfig.java:49`의 기존 Authentication 권한을 사용하고, Service도 `UserService.java:118`, `NoticeService.java:88`에서 기존 세션 객체의 authority만 확인한다. 현재 코드에는 다른 세션의 갱신·무효화 경로가 없다.

재현: 관리자 A와 B로 각각 로그인한다. B가 A의 authority를 false 또는 근무상태를 퇴사로 변경한다. A의 기존 쿠키와 CSRF 토큰으로 `/api/users/{번호}` 수정 또는 공지 작성을 요청한다. 기존 인증과 `loginUser`가 여전히 관리자이므로 요청이 허용되는 경로가 남아 있다. 퇴사 상태 검증은 이후 새 로그인에만 적용된다. 부서 이동도 `NoticeService.java:79`의 AI 공지 검색에 이전 부서 정보를 계속 공급한다.

영향: 권한을 회수하거나 퇴사 처리한 계정이 기존 세션으로 사원·공지 데이터를 변경할 수 있고, AI 검색 범위도 현재 부서와 달라진다.

최소 수정: 공통 요청 경계에서 현재 계정의 존재·재직 상태를 검증하고, 권한/부서 변경 때 Authentication과 `loginUser`를 함께 갱신하거나 해당 계정의 기존 세션을 무효화한다. Service 관리자 확인에서도 현재 권한과 일치해야 한다. 계정 변경 시 HTTP 세션을 실제 무효화하면 현재 Tomcat의 SEC-02 내장 소켓 종료 경로도 사용할 수 있다. DB만 수정한 경우에는 그 경로가 실행되지 않는다.

필수 회귀: 두 관리자 세션을 유지한 채 권한 회수·퇴사·부서 이동을 각각 적용한다. 기존 쿠키/CSRF로 관리 작업은 거부되고 AI 검색이 최신 범위를 사용해야 한다. 새 로그인 거부만 확인해서는 이 경로를 검증할 수 없다.

실행 결과: 주 에이전트의 격리 probe에서 authority=false로 변경한 뒤 기존 관리자 세션 GET/POST는 200, 새 세션은 403이었다. 퇴사 처리 뒤 기존 세션은 200, 새 로그인은 401이었다. 부서 이동/AI 범위 변경과 열린 WebSocket의 DB 퇴사 반영은 아직 이 결과로 검증하지 않았다.

## SEC-02 — 철회: HTTP 로그아웃 후 WebSocket 유지 가설

초기 정적 검토는 앱 소스에 세션 파괴 Listener가 없다는 이유로 기존 연결 회수가 없다고 판단했다. 주 에이전트의 격리 실행에서 정상 native CONNECT 뒤 동일 세션으로 로그아웃하자 이후 send가 Connection closed로 차단되었다. 이 결과에 따라 취약점 판정을 철회했다.

설치된 `tomcat-embed-websocket/11.0.24`의 bytecode를 javap으로 확인했다. `WsSci.init()`은 `WsSessionListener`를 ServletContext에 등록한다. `WsServerContainer.registerSession()`은 Principal과 HTTP sessionId가 있는 열린 소켓을 세션별로 등록하고, `WsSessionListener.sessionDestroyed()`는 `closeAuthenticatedSession(sessionId)`를 호출한다. 해당 메서드는 등록된 소켓을 VIOLATED_POLICY(1008)로 닫는다. 프레임워크 내부에서 수행하는 보호를 앱 소스 검색만으로 누락했던 것이다. [Tomcat 공식 WsServerContainer 소스](https://raw.githubusercontent.com/apache/tomcat/11.0.x/java/org/apache/tomcat/websocket/server/WsServerContainer.java)

최종 native 관측 결과: 로그아웃은 302, HTTP authenticated=false, 실제 WebSocket close code=1008, 로그아웃 이후 메시지 DB 저장=false였다. [실행 결과](review-evidence/results.json)의 native_websocket_observations에서 확인했다. 로그아웃에 대한 새 소켓 관리 구현을 요구하지 않는다. HTTP 세션 만료도 같은 sessionDestroyed 경로를 사용하지만 이번 로그아웃 관측만으로 만료 타이밍 테스트까지 완료했다고 표현하지 않는다. DB 퇴사·권한 변경은 현재 HTTP 세션을 무효화하지 않으므로 SEC-01과 별도로 열린 연결의 상태 변경 반영을 검증해야 한다.

## SEC-03 — 결재 대상 후보 제한이 저장 경로에 없다

근거: `src/main/resources/mapper/approval_mapper.xml:66`의 화면 후보는 작성자와 관리자 계정을 제외한다. 반면 `src/main/java/com/erp/service/ApprovalService.java:31`, `:75`의 생성·수정은 결재자 목록이 비어 있는지만 확인한다. `:202`는 전달된 번호를 그대로 결재선으로 만들고 Mapper `:91`은 FK로 계정 존재만 확인한다. 후보 SQL 자체에도 재직 상태 조건은 없다.

재현: 일반 사원이 유효 CSRF와 함께 `/approval/write.do`에 자신의 번호를 `approval_target`으로 전달한다. 자신의 대기 결재선이 저장되고, `/approval/updateComment.do`에 승인 요청을 보내면 `ApprovalService.java:139`의 첫 대기 결재자 검사도 통과하여 문서를 최종 승인할 수 있다. 퇴사한 계정 번호도 존재하는 한 결재자로 등록할 수 있다. 수정 요청에서도 동일한 제한 우회가 가능하다.

영향: 화면에서 제외한 작성자 자신을 서버 요청으로 결재자로 지정해 승인할 수 있다. 퇴사 계정을 지정하면 처리할 수 없는 대기 문서가 생성된다. 직급별 승인 규칙은 현재 코드의 정책에 없으므로 추가 결함으로 가정하지 않았다.

최소 수정: 생성·수정이 공유하는 Service에서 결재자 번호를 실제 계정의 canonical 번호로 확인하고, 중복·작성자 본인·화면 정책상 제외 계정·비재직 계정을 거부한다. 후보 SQL에도 같은 재직 조건을 적용한다. 문서 INSERT 전에 검사하여 실패 요청은 DB·파일 상태를 남기지 않는다.

필수 회귀: 본인, 퇴사 계정, 중복/대소문자 별칭, 존재하지 않는 번호를 생성·수정 요청으로 각각 제출한다. 적절한 400 응답과 기존 문서/결재선 보존을 확인한다. 정상 두 사원 순차 결재는 계속 동작해야 한다.

실행 결과: 주 에이전트의 격리 probe에서 자기 번호를 결재자로 등록한 생성 요청은 302, 본인 승인 요청도 302였으며 DB 문서 상태가 승인으로 바뀌었다. 퇴사 후보 등록과 수정 경로는 정적 확인 범위다.

## SEC-04 — 사원 hard delete가 승인 순서와 기록을 제거한다

근거: `src/main/java/com/erp/service/UserService.java:71`과 `:79`는 참조 확인 없이 삭제하고 `src/main/resources/mapper/user_mapper.xml:119`는 실제 DELETE를 실행한다. `document/ERP.sql:63`의 작성자 FK와 `:84`의 결재자 FK는 ON DELETE CASCADE이다. 따라서 작성자 삭제는 문서 전체를, 결재자 삭제는 해당 결재선만 지운다. 채팅 FK `:97`, `:110`도 방/메시지를 삭제한다. 반면 공지 작성자 FK `:44`는 같은 삭제를 제한하므로 데이터 구성에 따라 결과도 달라진다.

재현: 공지 작성 이력이 없는 결재자 A와 B를 순서대로 지정한 대기 문서를 만든다. 관리자가 A를 삭제하면 A의 결재선이 제거된다. 이후 B의 승인 요청은 첫 대기 결재자로 인정되어 A의 승인 없이 문서를 완료한다. 결재자 한 명만 있으면 삭제 후 대기 문서에 결재선이 없어 처리할 수 없다. 완료 문서 작성자를 삭제하면 완료 문서·결재선·첨부 메타데이터도 사라진다. 공지 작성자를 삭제하면 FK 오류가 발생하고, Service에는 의미 있는 참조 충돌 응답 처리가 없다.

영향: 사원 관리 작업이 승인 순서를 우회시키고 완료 기록을 영구 제거한다. DB 메타데이터가 사라져도 물리 첨부는 함께 제거되지 않는다.

최소 수정: 업무 기록이 참조하는 사원은 기존 `user_status`로 비활성화한다. hard delete가 필요하다면 참조를 확인하고 409로 거부하며, 기록 보존이 필요한 FK는 RESTRICT로 전환하는 명시적 마이그레이션을 설계한다. 삭제로 결재선을 재작성하는 방식은 승인 기록 보존을 해결하지 않는다.

필수 회귀: 선행/유일 결재자, 완료 작성자, 공지 작성자, 채팅 참가자의 단일·일괄 삭제를 격리 DB에서 각각 시도한다. 거부 또는 비활성화 뒤 문서·순서·승인 이력·대화 기록이 보존되어야 한다.

실행 결과: 주 에이전트의 격리 probe에서 첫 결재자 삭제는 200, 두 번째 결재자 승인은 302였고 문서는 승인으로 바뀌었다. 작성자 삭제 뒤 해당 완료 문서 수는 0이었다. 유일 결재자/공지/채팅/일괄 삭제의 추가 경우는 정적 확인 범위다.

## SEC-05 — 공백 비밀번호 수정이 해시를 손상한다

근거: `src/main/java/com/erp/service/UserService.java:62`는 isBlank인 입력의 인코딩을 건너뛰지만 입력을 null로 바꾸지 않는다. `src/main/resources/mapper/user_mapper.xml:108`은 null/빈 문자열만 제외하므로 공백 문자열은 UPDATE한다.

재현: 관리자 사원 수정 요청에 나머지 필수 정보를 정상 입력하고 `userpw`를 공백 한 칸으로 전달한다. DB의 기존 인코딩 비밀번호가 공백으로 교체된다. 해당 값은 기존 BCrypt 또는 legacy MD5 형식이 아니므로 이후 로그인이 실패한다.

영향: 비밀번호를 변경하지 않으려는 공백 입력으로 계정 로그인이 불가능해진다.

최소 수정 및 회귀: 업데이트에서 blank 비밀번호는 null로 정규화하여 기존 해시를 유지한다. null/빈 문자열/공백은 이전 해시 유지, 일반 입력은 인코딩·새 비밀번호 로그인 성공을 확인한다.

실행 결과: 주 에이전트의 격리 probe에서 공백 원문이 DB 비밀번호에 저장되었고 기존 비밀번호 로그인은 401이었다. 비밀번호 값과 해시는 보고서에 복사하지 않는다.

## SEC-06 — 결재 첨부 교체·삭제에서 물리 파일이 남는다

근거: `src/main/java/com/erp/service/ApprovalService.java:88`의 첨부 교체는 기존 파일 DB 행만 삭제한다. `:95`와 `src/main/java/com/erp/control/ApprovalController.java:56`의 문서 삭제에도 실제 파일 제거가 없다. Controller `:111`, `:135`의 cleanup은 신규 저장 뒤 Service 실패한 경우만 처리한다.

재현: 대기 문서에 파일 A를 올린 뒤 수정하여 파일 B로 교체하고 문서를 삭제한다. A와 B는 DB에서 더 이상 연결되지 않지만 uploads에 남는다. DB 사원 CASCADE 삭제에서도 같은 불일치가 생긴다.

영향: 정상 사용만으로 삭제한 첨부 데이터와 파일 용량이 누적된다. DB만 백업·복구한 경우 파일 참조와 실파일의 차이를 확인하기 어렵다.

최소 수정: 변경 트랜잭션에서 기존 파일명을 확보하고, 성공 commit 이후 더 이상 참조되지 않는 파일만 제거한다. DB 실패 시 기존 파일을 먼저 삭제하지 않는다. 제거 실패는 추적 가능한 로그를 남긴다. 저장 중 IOException cleanup도 저장 경로를 소유한 코드에서 처리해야 한다. 현재 `ApprovalController.java:106`, `:159`와 `NoticeController.java:88`, `:118`의 transferTo는 Service 예외 cleanup 밖에 있으므로 부분 저장 후 I/O 실패 경로는 별도 검증이 필요하다.

필수 회귀: 정상 생성·교체·삭제의 DB와 파일 바이트를 함께 확인하고, 교체의 DB 실패에서는 기존 파일이 유지되고 신규 파일은 제거되어야 한다. 부분 저장 I/O 실패는 주입 가능한 MultipartFile로 격리 검증한다.

## SEC-07 — 공지의 요청 파일명이 실패 cleanup에 사용된다

근거: `src/main/java/com/erp/control/NoticeController.java:84`는 `noticeVO`를 그대로 바인딩한다. `src/main/java/com/erp/vo/noticeVO.java:33`에 pname/fname setter가 있고 바인딩 allowlist가 없다. 첨부가 없으면 `NoticeController.java:112`는 전달된 pname을 지우지 않는다. Service 검증 실패 후 `:93`, `:123`이 그 파일명을 삭제한다. 정상 생성도 `src/main/resources/mapper/notice_mapper.xml:41`에 해당 메타데이터를 저장한다.

재현 조건: 관리 권한과 CSRF, uploads 내부의 기존 파일명 사전 지식이 필요하다. 격리 fixture의 `sentinel.bin` 파일을 준비하고, 첨부 없이 `pname=sentinel.bin`과 공지 제목/본문을 제출하되 notice_team을 생략한다. Service가 400으로 실패하면서 기존 sentinel 파일을 삭제한다. 팀을 정상 입력하면 다른 기존 파일에 대한 별칭 공지 메타데이터도 생성할 수 있다.

영향: 실패 요청이 그 요청에서 생성하지 않은 파일을 삭제하거나 잘못된 파일 참조를 만든다. UUID 파일명은 일반 공지 API에 노출되지 않으므로 이를 일반 사원의 임의 파일 삭제로 확대하지 않았다. normalize/startsWith 검사가 있어 업로드 디렉터리 밖의 삭제를 주장하지 않는다.

최소 수정 및 회귀: fname/pname을 외부 바인딩 대상에서 제외하고, 실제 업로드가 생성한 Path만 반환하여 실패 cleanup에 사용한다. 요청 pname에 기존 파일명을 넣어도 무시되고, validation 실패 후 sentinel과 기존 공지 첨부가 유지되는지 확인한다.

## SEC-08 — DB 조회 결과의 canonical 사원번호를 방 저장에 사용하지 않는다

근거: `src/main/java/com/erp/service/ChatService.java:32`에서 partner 계정을 조회하지만 `:37`은 원문 partnerUsernum을 정렬·저장한다. user 및 chat 테이블은 `document/ERP.sql:32`, `:101`의 utf8mb4_unicode_ci로 대소문자를 동일하게 비교한다. `src/main/java/com/erp/control/ChatController.java:77`은 저장된 상대 번호를 `convertAndSendToUser` 대상으로 사용한다. 실제 로그인 Principal은 `LoginService.java:37`의 DB 번호를 사용한다.

재현: DB의 `admin` 상대를 `/api/chat/rooms` 요청에서 `ADMIN`으로 지정한다. DB lookup/FK는 성공하고 방에는 원문 ADMIN이 저장된다. 상대의 실제 Principal은 admin이므로 메시지 저장 뒤 다른 이름의 user destination으로 전송되어 상대 실시간 수신이 누락된다. admin 사용자가 상대를 ADMIN으로 지정하면 처음의 Java equals 자기 검사도 통과하여 논리적으로 자신과의 방을 만들 수 있다. 영문 custom 번호가 섞인 경우 원문 대소문자가 정렬을 바꾸어 역방향 중복 방도 만들 수 있다.

영향: 정상 DB 계정 lookup에 성공한 요청이 실시간 통신 실패 또는 잘못된 대화방을 만든다. 숫자 사원번호만 입력한 기존 정상 테스트는 이 경로를 다루지 않는다.

최소 수정 및 회귀: DB에서 조회한 `partner.getUsernum()`으로 정규화한 후 자기 여부를 검사하고 두 번호를 정렬한다. 대소문자 별칭으로 방 생성/반대 방향 재사용/자기 상대 거부/양방향 실시간 송수신을 확인한다.

## SEC-09 — 완료 결재 count에 부서 조건이 없다

근거: `src/main/java/com/erp/control/ApprovalApiController.java:74`는 동일 params를 목록과 count에 전달한다. `src/main/resources/mapper/approval_mapper.xml:357` 목록은 작성자 부서 조건을 적용하지만 `:364`의 count에는 user JOIN과 team 조건이 없다.

재현: 서로 다른 두 부서에 승인 완료 문서를 만들고 `/api/approvals/completed?team=개발`을 조회한다. items는 개발 부서로 제한되고 totalCount/totalPages는 전체 부서의 개수로 계산된다.

영향: 검색 결과 개수와 페이지가 실제 목록과 달라 마지막 페이지가 비는 등 탐색 결과가 잘못된다.

최소 수정 및 회귀: 목록과 count의 JOIN·필터 조건을 일치시킨다. 두 부서 fixture에서 items, totalCount, totalPages를 함께 확인한다.

실행 결과: 주 에이전트의 격리 probe에서 부서 필터가 적용된 목록은 1건, count는 2건이었다.

## SEC-10 — 큰 page 입력을 검증하지 않아 offset이 음수로 넘친다

근거: `src/main/java/com/erp/control/ApprovalApiController.java:42`, `:58`, `:72`는 하한만 제한하며 `:113`은 int로 `(page - 1) * 10`을 계산한다. User/Notice 목록도 각각 `UserApiController.java:50`, `NoticeApiController.java:36`과 `src/main/java/com/erp/vo/searchVO.java:33`의 같은 계산을 사용한다.

재현: 인증 상태에서 page=2147483647을 제출한다. int offset은 -20으로 넘치고 MyBatis가 이를 LIMIT 입력으로 전달한다. 유효한 int 파라미터여서 Controller 단계의 변환 오류로 차단되지 않는다.

영향: 사용자 입력으로 잘못된 SQL 범위를 전달하여 목록 요청이 서버 오류로 끝난다. 데이터 전체 노출이나 SQL injection으로 확대하지 않는다.

최소 수정 및 회귀: 공통 paging 입력에 상한/범위를 적용하거나 checked long 계산과 유효 범위 거부를 사용한다. 음수·0·최대 int 요청을 각 목록에서 확인하고 잘못된 입력은 400, 정상 페이지는 기존 결과를 유지한다.

실행 결과: 주 에이전트의 격리 probe에서 page 최대 int 입력으로 결재·공지·사원 목록 API 모두 500을 반환했다.

## 운영 설정과 배포 설계의 경계

현재 `src/main/resources/application.properties:6`의 기본 DB 연결은 개발용이며 `:7`, `:8`에 개발 자격증명 fallback이 있다. `compose.yaml:6`, `:8`, `:9`, `:20`에도 자격증명 literal이 있다. 값은 이 보고서에 복사하지 않는다. Compose의 `:11` 포트 게시에는 loopback 바인딩이 없으므로 이 개발 구성을 외부 접근 가능한 호스트에서 그대로 실행하면 DB 포트 노출 범위가 넓어진다.

`application.properties:10`은 sql.init=always, `:16`은 상대 uploads 경로다. 사용자의 최신 지시에 따라 현재 실행 목표는 Docker 없이 native MySQL/WAR/절대 uploads를 분리한 개발 환경이며, 도구의 생성 설정이 해당 기본값을 덮어쓴다. 운영 profile/Compose/이미지 구현은 현재 요청 범위가 아니다. 개발 설정을 그대로 공개 배포하는 것을 승인된 운영 구성으로 간주할 수 없고, 운영 환경의 자격증명·계정 권한·외부 포트·TLS를 이 검토자가 확인한 사실은 없다.

별도 [분리 환경·복원 설계](deployment-readiness-design.md)의 전용 datadir/socket/loopback 포트, DB 관리 계정과 앱 DML 계정 분리, 절대 uploads, 새 DB·새 uploads 복원 계약을 native 실행 결과와 대조한다. 이 환경 분리는 SEC-01·03·04를 해결한 구현이나 회귀 결과를 대신하지 않는다. 기존 P1의 제품 수정은 이번 환경 분리 요청의 구현 범위에 포함하지 않으며, 공개 운영 전 필요한 후속 작업으로 남긴다.

## 확인한 보호와 의도된 정책

Mapper에서 사용자 입력을 SQL 문자열로 삽입하는 `${}` 경로는 찾지 못했다. 검색은 `#{}`를 사용하고 사원 검색 컬럼은 choose로 제한한다. 결재 번호·제목 AI 검색도 prepared 조건을 사용하며, 현재 결재자·선행 승인·종결 상태 조건은 모델 호출 전에 서버 SQL로 제한한다.

결재 승인·수정·삭제는 같은 문서의 FOR UPDATE를 거치고, 결정은 승인/반려 allowlist와 첫 대기 결재자 규칙을 확인한다. 연간 사원번호는 DB sequence 행 잠금과 PK 제약을 사용한다. 메시지 저장은 참가자 권한 확인 및 부모 방 행을 먼저 갱신한다. 이 코드 보호가 SEC-03의 결재자 등록 정책 누락이나 SEC-04의 FK cascade를 상쇄하지는 않는다.

WebSocket은 native Spring Security CONNECT CSRF, 허용된 `/app/chat.send`·sync 송신과 특정 `/user/queue/*` 구독을 검사하고 일반 MESSAGE/SUBSCRIBE를 거부한다. 순서 보존과 별도 sync 준비 응답이 복구 시 history 조회 순서를 구성한다. 기존 CONNECT/금지 경로 보호와 HTTP 세션 종료 후 연결 회수는 다른 조건이다.

공지의 전체 로그인 사용자 열람과 최종 승인 문서의 전체 열람은 README의 명시 정책이므로 부서별 IDOR로 분류하지 않았다. AI 공지 검색은 별도의 사용자 부서·관리 권한 범위를 사용하며 SEC-01의 세션 최신성 문제가 적용된다.

파일 저장 이름은 서버 생성 UUID이고 다운로드 경로는 normalize/startsWith 및 regular file 검사를 사용한다. 서버 파일 시스템에 별도 쓰기 권한이 필요한 symlink 공격을 현재 원격 요청의 재현 가능한 문제로 분류하지 않았다. 공지는 표준 ContentDisposition builder를 사용하지만 결재 다운로드의 수동 파일명 header와 여러 첨부를 같은 다운로드 URL로 표시하는 계약은 후속 파일 기능 검증 범위로 남는다.

AI는 자료를 참고 데이터로 구분하는 지시를 넣고 서버 권한 검색 결과만 전달한다. 모델에 DB 수정 도구나 실행 도구를 제공하는 경로는 없다. 모델 응답은 HTTP 200·done:true·비어 있지 않은 답변과 본문 종료를 확인하며, 오류·완료 뒤 추가 이벤트·전체 deadline 초과를 실패로 처리한다. SseEmitter timeout/error/completion은 worker interruption으로 연결되어 있다. 취소 요청이 실제 모델 계산을 즉시 종료한다는 측정은 수행하지 않았다. 실제 모델 비교의 키워드 검사 결과는 일반 답변 정확도나 prompt injection 불가능의 증거가 아니다.

## 검증 한계와 배포 전 통과 조건

이 검토자가 수행한 작업은 호출 경로·DB DDL·환경 도구의 정적 리뷰와 주 에이전트가 보관한 실행 근거의 대조다. 배포 환경 침투 테스트, 의존성 CVE 조회, HTTP·WebSocket 실행, 새 DB 복원, 외부 네트워크 차단, 부하 측정을 직접 수행하지 않았다. native 로그아웃·복원 등의 실행 관측은 위에 출처를 명시했다. 이전 단계의 테스트 통과 사실을 이번 신규 경로의 검증으로 확대하지 않는다.

배포 전에는 SEC-01·03·04의 수정과 해당 회귀, 정상 사용자·관리자의 핵심 흐름, 동시 승인·사원번호·양방향 메시지 검증, 운영 설정 분리와 DB/파일 함께 복원 검증을 완료해야 한다. 현재 단계의 성능 수치와 배포 실증 여부는 각각 별도 [성능 측정 설계](performance-measurement-design.md)와 배포 준비 설계에 기록한다.

## 추가 검토 — native 분리 환경 관리 도구

`scripts/dev-environment.py`와 `scripts/verify-dev-recovery.py`의 최초 구현과 보완 이후 구현을 읽기 전용으로 검토했다. 이 검토자는 도구를 실행하거나 스크립트·제품·테스트 소스를 수정하지 않았다. 아래에는 최초 재현 조건, 현재 수정 코드, 실행 검증 상태를 구분한다. [native-recovery.json](review-evidence/native-recovery.json)은 정상 업무·백업/복구와 부적격 입력 검사 결과다. 이후 archive_members 공통 함수·MySQL client 경계 보완의 실행 근거는 [native-client-guard.json](review-evidence/native-client-guard.json)과 [native-final-restore.json](review-evidence/native-final-restore.json)에서 별도로 확인했다.

| 항목 | 최초 코드 근거·재현 조건 | 현재 수정 근거 | 검증 상태 |
|---|---|---|---|
| NATIVE-01 환경 루트 중첩 | BASE 아래 임의의 `.local-dev/source/uploads/restore`도 허용 | managed_root:44는 BASE 직접 자식만 허용하고 root symlink/backups를 거부 | 코드 해결. 주 에이전트 native 부적격 root 거부 PASS |
| NATIVE-02 uploads 자체 symlink | rglob 결과만 검사하여 uploads 루트 자체 symlink는 제외 | load:72는 mysql/data/uploads/run/logs 등 critical 경로의 symlink와 root 밖 해석을 거부하고 :79는 생성 앱 경로·포트·DB·sql.init 설정을 대조 | 코드 해결. 주 에이전트 native uploads 루트 symlink 거부 PASS |
| NATIVE-03 root 권한 SQL import | checksum과 manifest를 함께 바꾼 SQL을 관리 root 권한으로 import | restore:355는 임시 erp_restore@localhost에 erp.*만 GRANT ALL하고 :359는 해당 계정으로 import. finally에서 DROP USER/config 삭제. mysql_client_args:87은 socket과 login-path 배제를 강제하고 SQL·비밀번호는 stdin 사용 | 코드 해결. 주 에이전트 정상 import/복원 및 임시 계정 삭제 PASS. 악성 SET GLOBAL 실제 권한 거부 회귀는 미확인 |
| NATIVE-04 동시 start/init 경쟁 | PID 확인→spawn→저장과 빈 root 확인→설정 생성 사이 잠금 없음 | control_lock:52의 LOCK_EX/LOCK_NB를 CLI entry:420과 verify-dev-recovery.py:302에서 전체 실행에 적용. 중복 명령은 변경 전에 거부 | 코드 해결. 주 에이전트 native 경쟁 CLI 거부 PASS |
| NATIVE-05 schema 검증 누락 | 행/count와 첨부/FK 고아 데이터만 비교 | snapshot:252는 TABLES·COLUMNS·STATISTICS·TABLE_CONSTRAINTS·KEY_COLUMN_USAGE·REFERENTIAL_CONSTRAINTS·CHECK_CONSTRAINTS의 안정적 metadata 및 AUTO_INCREMENT를 hash. sql:97은 정보 스키마 통계 캐시를 끄고 restore:369에서 전체 snapshot 비교 | 코드 해결. SHOW CREATE의 중복 CHARACTER SET 표현 차이를 피하며 의미 있는 schema 비교 유지. 주 에이전트 정상 복원 snapshot 일치 PASS |
| NATIVE-06 tar 재open 후 검증 누락 | 초기 검사한 tar를 닫은 뒤 초기화/import 이후 다시 열어 새 목록을 검사 없이 추출 | archive_members:328을 초기 검사 :352와 실제 추출 :364 모두에서 호출하고 그 검증된 목록만 순회 | 코드 해결. traversal 초기 거부 및 공통 함수 보완 이후 정상 복원 PASS. 첫 검사 후 archive 교체 자체의 회귀는 미확인 |
| NATIVE-07 mysql 로컬 명령 실행 | 최초 sql은 --batch만 사용하여 SQL 파일의 mysql client 로컬 명령을 제한하지 않음. DB 제한 계정과 별개인 OS 사용자 권한 경계 | mysql_client_args:90의 --binary-mode를 stdin SQL import와 공통 조회에 적용. verify-dev-recovery.py:71에 임시 경로의 client 명령 거부 회귀 추가 | 코드 해결. native-client-guard.json 및 최종 full verifier의 명령 거부·sentinel 미생성·원본 유지 PASS. 정상 복원도 PASS |
| NATIVE-08 전역 login-path 영향 | --defaults-file도 .mylogin.cnf를 읽음. 최초 bootstrap/seed/readiness/dump는 별도 argv socket 강제가 없어 해당 설정이 다른 서버를 선택 가능 | mysql_client_args:87은 --no-login-paths·소유 socket/protocol을 모든 mysql/mysqladmin/mysqldump 호출에 적용. 실제 사용자 전역 설정은 수정하지 않음 | 코드 해결. 전용 MYSQL_TEST_LOGIN_FILE의 잘못된 사용자/socket 설정 아래 소유 서버 stop/start·조회·dump PASS. 정상 초기화/복원도 PASS |
| NATIVE-09 앱 환경변수 우선순위 | 최초 spawn/start는 OS 환경을 상속하고 additional-location 파일만 지정. env/system property/SPRING_APPLICATION_JSON은 그 파일보다 우선 | app_environment:108은 대소문자·dotted 이름을 정규화하여 격리 관련 Spring/server/DB/uploads·JVM 주입 변수를 제외. start:228은 해당 env를 Java 자식에만 전달하고 ERP_AI_* 보존 | 코드 해결. 실제 override 상태에서 소유 포트·API/SQL DB 건수 일치·작성자 첨부 다운로드 bytes 일치·원본 snapshot 유지 PASS |
| NATIVE-10 백업 출력 루트 symlink | 최초 backup의 BASE/backups는 critical symlink 검사 밖이며 외부 디렉터리 symlink인 경우 dump/tar/manifest를 외부에 생성 | backup:294는 parent symlink/소유 범위를 확인하고 실패 시 app stop 전에 거부 | 코드 해결. 실제 backups를 바꾸지 않는 전용 임시 base에서 symlink 거부·외부 출력 미생성·source 앱 PID 유지 PASS |

NATIVE-03의 checksum은 전송 손상을 확인하는 보호이며 백업 발행자의 신뢰를 인증하지 않는다. 원본 source DB와 restore DB는 생성된 별도 socket/datadir를 사용하므로 정상 자기 백업 import가 원본을 수정한다는 결함을 주장하지 않는다. 악의적 SQL에 대한 권한 경계와 정상 DB 분리는 다른 검증이다.

현재의 `GRANT ALL ON erp.*`는 DB 수준의 권한이며 FILE/SUPER 등의 전역 권한을 포함하지 않는다. SET GLOBAL에는 SYSTEM_VARIABLES_ADMIN 또는 SUPER가 필요하므로 현재 신규 restore 계정의 권한은 지적한 전역 설정·파일 쓰기 경계를 제한한다. 이는 코드·공식 권한 규칙 확인이며 악성 SQL을 실제 실행하여 거부를 관측한 결과와 구분한다. [MySQL GRANT 권한 수준](https://dev.mysql.com/doc/refman/8.4/en/grant.html), [시스템 변수 권한](https://dev.mysql.com/doc/refman/8.4/en/system-variable-privileges.html)

NATIVE-07은 MySQL 서버가 실행하는 SQL과 별개인 client 명령의 문제다. binary-mode는 stdin/source의 noninteractive 실행에서 charset/delimiter 외의 client 명령을 끈다. NATIVE-08은 defaults-file 지정의 예외이며, 보완 전에는 전역 login-path가 기존 서버의 root 접속으로 구성된 경우 초기화의 ALTER USER나 개발 seed가 그 서버로 전달될 수 있었다. 해당 전역 설정이 실제 머신에 존재한다고 추정하거나 사용자 파일을 읽지 않았다. 현재 공통 옵션은 login-path 읽기를 끄고 소유 socket을 강제한다. [MySQL client 옵션](https://dev.mysql.com/doc/refman/8.4/en/mysql-command-options.html), [옵션 파일 처리 순서](https://dev.mysql.com/doc/refman/8.4/en/option-files.html), [mysqldump login-path 옵션](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)

NATIVE-09의 보완 전 조건은 ERP_UPLOAD_DIR, SPRING_DATASOURCE_URL/USERNAME/PASSWORD, SERVER_PORT/ADDRESS, SPRING_APPLICATION_JSON 또는 JVM 주입 변수의 -D 설정이 있는 상태에서 start하는 것이었다. 생성 파일 내용이 정상이어도 실제 실행 설정이 달라질 수 있었다. 해당 환경 변수가 실제 머신에 있다고 가정하거나 값을 출력하지 않았다. 현재는 Java 자식 프로세스에만 격리 관련 환경을 제외한 명시 env를 전달한다. dotted/lowercase 이름도 정규화하며 ERP_AI_* 사용자 모델 설정은 보존한다. 파일 내용 검사와 실행 시 property source 고정은 다른 보호다. [Spring Boot 외부 설정 우선순위](https://docs.spring.io/spring-boot/reference/features/external-config.html)

최신 실행 기록 대조: 모든 도구 보완 이후 full verifier는 mysql client 명령·전역 login-path/Java override·백업 parent symlink 차단, 소유 DB·포트·첨부 bytes, 재기동·백업·새 복원·다운로드 권한 403·동시 번호 2개·원본 불변을 통과했다. 해당 native-recovery.json의 복원본 `.local-dev/restore-aa9092b5`는 동시 번호 검증으로 원래 snapshot에서 변경되었으므로 별도 pristine 복원도 구분한다. 백업 `.local-dev/backups/20261006T034547Z-903e5d`의 pristine 복원본 `.local-dev/restore-isolation-final`에서는 schema·rows·files 일치, 원본 불변, 임시 복원 계정 제거와 복원본 종료를 확인했다고 주 에이전트가 기록했다. 7.974초는 이 소량 fixture의 pristine 복원 실행시간이며 운영 성능 측정값이 아니다. SET GLOBAL 권한 거부와 첫 검사 후 archive 동시 교체는 코드 보호를 확인했지만 공격 조건 자체의 실행 근거는 없다.

[stage5-6-design-review.md](stage5-6-design-review.md)의 회귀 57개 중 56개 통과/선택 모델 평가 1개 생략, Node 7개, native 로그아웃 1008·미저장, 기존 제품 P1 미수정 구분은 results.json과 일치한다. manifest.json에 보관한 schema 두 파일과 scripts 두 파일의 SHA-256을 현재 파일과 직접 대조했다. 네 파일 모두 일치하며 최종 도구 실행 이후 manifest가 갱신되었다. native-recovery.json의 전체 업무·복구 검증과 native-final-restore.json의 pristine 복원은 서로 다른 실행으로 구분한다. 성능 원문 측정, Linux/다른 머신·TLS/프록시·디스크 장애 복구는 이 native 개발 검증에 포함하지 않는다.

현재 도구는 비어 있지 않은 초기화 대상 거부, BASE 밖 root 거부, 무작위 비밀번호를 mode 600 설정에 저장, loopback DB/app 바인딩, 비밀번호를 stdin/설정 파일로 전달, WAR/payload SHA-256 비교를 구현했다. tar의 절대 경로·..·symlink·hardlink·special member 거부, manifest의 member 목록·size 대조는 초기 단계와 실제 추출 단계 모두에 적용된다. 추출 후 파일 size/hash도 snapshot으로 비교한다. 설정·dump의 실제 비밀번호 값은 이 보고서에 기록하지 않는다.

백업은 소유 앱을 정지한 뒤 snapshot/dump/archive를 생성하고 원래 실행 중이었다면 재기동한다. manifest는 먼저 exported로 기록하고 재기동 성공 후 complete로 전환하며, restore는 complete만 허용한다. status의 DB 상태도 socket 파일 존재 대신 실제 SELECT 1로 확인한다. 이 보장은 정확한 프로세스 소유 확인과 동시 명령 제어에 의존하며, 외부 수동 DB writer/DDL·파일 수정을 함께 금지하는 계약이 필요하다. 실제 source 초기화·기동·백업·새 환경 복원·정상 종료 결과와 기존 환경 보존은 주 에이전트의 [native 실행 기록](review-evidence/native-recovery.json)과 최신 도구 변경 이후 실행 여부를 함께 확인해야 한다.
