# 사내 메신저 MVP 구현 기록

## 구현 범위

1. 사원 간 1:1 대화방 생성
2. Spring WebSocket STOMP와 내장 SimpleBroker 기반 실시간 메시지 전달
3. MySQL 메시지 이력 저장
4. 읽음 상태와 사용자별 안 읽은 메시지 수
5. 3초 간격 자동 재접속과 재접속 후 이력 재조회
6. 모든 대화방 조회·전송·읽음 처리의 참여자 권한 검사

단체방, 파일 전송, 검색, 반응, 입력 중 표시, Redis와 Kafka는 제외했다. 단일 서버 부하 측정에서 SimpleBroker의 한계가 확인되기 전에는 추가하지 않는다.

## 구조

```text
React Messenger
  ├─ REST: 대화방·이력·읽음 처리
  └─ STOMP: 실시간 메시지 송수신
          ↓
ChatController
          ↓
ChatService (@Transactional)
          ↓
ChatMapper
          ↓
chat_mapper.xml → MySQL
```

메시지는 MySQL 트랜잭션이 완료된 뒤 발신자와 수신자의 개인 큐로 전달한다. 브로커 접속 중 발생한 누락 가능성은 재접속 시 DB 이력을 다시 조회해 복구한다.

## 데이터와 인덱스

`chat_room`은 두 사원번호를 정렬해 `(user_a, user_b)` 유니크 키로 중복 1:1 방 생성을 막는다.

`chat_message`는 메시지 본문, 발신자, 전송 시각, 읽은 시각을 저장한다. 이력 조회용 `(room_no, message_no)`와 안 읽은 수 집계용 `(room_no, sender_usernum, read_at)` 인덱스를 추가했다.

## 권한 처리

클라이언트가 보내는 발신자 정보는 신뢰하지 않는다. HTTP 세션에서 복원된 Spring Security `Principal`을 발신자로 사용한다.

대화 이력 조회, WebSocket 전송, 읽음 변경 전마다 현재 사용자가 `chat_room`의 참여자인지 검사한다. 비참여자의 이력 조회는 HTTP 403으로 차단된다.

## 검증 결과

1. `npm run build` 성공
2. Testcontainers MySQL 기반 전체 통합 테스트 18개 통과
3. 비참여 사용자 대화 이력 접근 시 HTTP 403 확인
4. 브라우저에서 실시간 메시지 전송 후 새로고침해도 이력 유지 확인
5. 수신자 안 읽은 메시지 배지 1 표시 후 대화방 진입 시 제거 확인
6. 발신자 화면에서 `안 읽음`이 `읽음`으로 변경되는 것 확인

## 트러블슈팅

### Testcontainers가 시작되지 않음

증상: `./mvnw test` 실행 시 `Could not find a valid Docker environment` 오류가 발생했다.

원인: 테스트 코드 문제가 아니라 Docker Desktop이 실행되지 않아 MySQL 테스트 컨테이너를 만들 수 없었다.

해결: Docker Desktop을 시작한 뒤 동일 명령을 다시 실행했고 18개 테스트가 통과했다.

### 대화방 목록 객체가 null로 반환됨

증상: 메시지 저장 후 대화방 목록의 `ChatRoomVO`가 null로 반환되어 통합 테스트가 실패했다.

원인: MyBatis 기본 설정에서 DB의 `room_no` 같은 snake_case 컬럼이 Java의 `roomNo`에 자동 매핑되지 않았다.

해결: 전역 설정을 변경하지 않고 `chat_mapper.xml`에 `chatRoomMap`, `chatMessageMap`을 명시해 기존 도메인에 미치는 영향을 차단했다.

### 새 대화 버튼 글자가 세로로 접힘

증상: 기존 AI 채팅 패널의 공통 버튼 CSS가 메신저의 `대화 시작` 버튼에도 적용돼 너비가 42px로 제한됐다.

해결: `.messenger-panel .messenger-new button` 범위에서 너비와 높이를 명시해 기존 AI 채팅 디자인을 유지하면서 충돌만 제거했다.

## 성능 측정 메모

현재 단계에서는 Redis Pub/Sub을 추가하지 않았다. 다음 단계에서 동시 WebSocket 연결 수, 초당 메시지 처리량, p95 전달 지연, DB 저장 p95, 오류율을 측정하고 SimpleBroker의 한계가 재현될 때만 외부 브로커를 검토한다. 측정 전에는 성능 개선 수치를 포트폴리오에 사용하지 않는다.
