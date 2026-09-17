# 전자결재 리팩터링 설계 및 검증 기록

- 작업일: 2026-09-17
- 범위: 테스트 DB 격리, CI, 전자결재 통합 테스트, Controller–Service–Mapper 분리
- 검증 명령: `./mvnw clean test`
- 검증 결과: 10개 테스트 성공, 실패 0, 오류 0, 건너뜀 0

## 1. 변경 목표

기존 전자결재 코드는 Controller와 DTO가 MyBatis `SqlSession`을 직접 호출하고, 한 번의 승인 요청에서 동일한 상태 갱신 SQL을 중복 실행했습니다. 테스트도 개발용 MySQL을 사용해 관리자 비밀번호와 업무 데이터를 변경할 수 있었습니다.

이번 작업은 다음 세 가지를 기준으로 진행했습니다.

1. 테스트는 개발 DB와 물리적으로 분리한다.
2. 업무 규칙과 트랜잭션 경계는 Service에 둔다.
3. 전자결재의 핵심 흐름과 권한을 실제 HTTP·Spring Security·MySQL 조합으로 검증한다.

## 2. 적용 구조

```text
HTTP 요청
  ↓
ApprovalController
  - 요청 파라미터, 세션 사용자, 화면 모델, 파일 입출력
  ↓
ApprovalService
  - 결재자 필수 검증
  - 승인·반려 규칙
  - 문서 접근 권한
  - 트랜잭션 경계
  ↓
ApprovalMapper
  ↓
approval_mapper.xml
  ↓
MySQL
```

기존 `approvalDTO`는 제거했습니다. 구현체가 하나뿐인 Service 인터페이스나 별도 Repository 계층은 만들지 않았습니다. 현재 규모에서는 `Controller → Service → Mapper`가 업무 규칙과 SQL을 분리하는 데 충분하기 때문입니다.

### Controller 책임

- HTTP 요청과 리다이렉트 처리
- 로그인 사용자와 검색 조건 구성
- JSP 모델 구성
- 첨부파일 저장·다운로드
- 잘못된 입력을 HTTP 400으로 변환
- 삭제 요청을 GET에서 CSRF 보호가 적용되는 POST로 변경

### Service 책임

- 결재자 한 명 이상 지정 검증
- 결재선 순서 생성
- 승인·반려 값 검증과 반려 의견 필수 처리
- 작성자·결재자·일반 사용자 열람 권한 처리
- 작성자만 대기·반려 문서를 수정하도록 제한
- 작성자만 대기 문서를 삭제하도록 제한
- 문서·첨부파일 메타데이터·결재선 변경을 하나의 트랜잭션으로 처리

### Mapper 책임

- MyBatis SQL 실행만 담당
- 승인 처리는 아직 `대기`인 본인의 결재선 한 건만 변경
- 문서 상태는 결재선 집계 결과로 한 번에 계산
- 상세 조회 단계에서 작성자·결재자·최종 승인 문서 접근 조건 적용

## 3. 트랜잭션 설계

다음 Service 메서드에 `@Transactional`을 적용했습니다.

| 메서드 | 원자적으로 처리되는 작업 |
|---|---|
| `createApproval` | 문서 작성, 첨부파일 메타데이터, 결재선 생성 |
| `processApproval` | 결재선 승인·반려, 문서 최종 상태 집계 |
| `modifyApproval` | 문서 수정, 결재선 재생성, 첨부파일 메타데이터 교체 |
| `deleteApproval` | 작성자·상태 조건을 포함한 문서 삭제 |

존재하지 않는 결재자를 넣어 외래 키 오류를 발생시킨 통합 테스트에서, 먼저 INSERT된 결재 문서까지 사라지는 것을 확인했습니다. 즉 문서와 결재선이 부분 저장되지 않고 전체 롤백됩니다.

파일시스템은 DB 트랜잭션 대상이 아니므로 업로드 후 DB 작업이 실패하면 Controller가 방금 저장한 파일을 삭제합니다. 삭제 자체가 실패하더라도 DB 롤백은 유지되며, 이 경우에는 고아 파일 정리 작업이 필요합니다.

## 4. 테스트 DB 격리와 CI

`CoreFlowIntegrationTest`는 Testcontainers의 `mysql:8.4` 컨테이너를 테스트 클래스 전용으로 실행합니다. `document/ERP.sql`을 컨테이너 초기화 디렉터리에 복사하고, `@DynamicPropertySource`로 해당 컨테이너의 JDBC URL과 계정을 주입합니다.

따라서 테스트 중 수행하는 관리자 MD5 비밀번호 재설정, BCrypt 자동 전환, 전자결재 INSERT·UPDATE는 개발용 `localhost:3307/erp`에 접근하지 않습니다. 검증 전후 개발 DB의 결재 문서 수와 관리자 BCrypt 비밀번호가 동일한 것도 확인했습니다.

GitHub Actions는 `main` 브랜치 push와 pull request에서 Java 21을 준비한 뒤 아래 명령을 실행합니다.

```bash
./mvnw --batch-mode test
```

로컬 테스트는 통과했지만, 이번 로컬 작업에서는 원격 GitHub Actions 실행 결과까지 확인하지 않았습니다. 워크플로는 다음 push 또는 pull request부터 실행됩니다.

## 5. 고정한 전자결재 업무 흐름

통합 테스트 10개가 다음 동작을 검증합니다.

| 번호 | 검증 내용 |
|---:|---|
| 1 | 로그인 화면 공개 접근과 CSRF 토큰 |
| 2 | 비로그인 사용자의 보호 화면 접근 차단 |
| 3 | 잘못된 로그인 정보 거절 |
| 4 | 기존 MD5 비밀번호 로그인 후 BCrypt 자동 전환과 핵심 화면 접근 |
| 5 | 문서 작성, 결재선 지정, 지정 순서와 초기 상태 |
| 6 | 1차 승인 시 `진행중`, 전체 승인 시 `승인`으로 변경 |
| 7 | 반려 의견 필수 검증과 최종 `반려` 상태 |
| 8 | 결재선에 없는 사용자의 승인 차단 |
| 9 | 최종 승인 전 작성자·결재자만 열람, 승인 후 일반 사용자도 열람 |
| 10 | 결재선 저장 실패 시 문서 작성까지 전체 롤백 |

승인·반려와 열람 권한은 임의 메서드 호출만으로 끝내지 않고, 실제 랜덤 포트 서버에 로그인하고 CSRF 토큰과 세션 쿠키를 포함한 HTTP 요청으로도 검증했습니다.

## 6. 트러블슈팅 기록

### 개발 DB가 테스트로 변경되는 문제

- 증상: 기존 통합 테스트가 개발 DB의 `admin` 비밀번호를 MD5로 바꾼 뒤 로그인하여 BCrypt로 재변환했습니다.
- 원인: 테스트와 개발 실행이 같은 datasource 설정을 사용했습니다.
- 해결: Testcontainers 전용 MySQL과 동적 datasource 설정을 사용해 물리적으로 분리했습니다.
- 재발 방지: CI도 동일한 테스트 코드를 실행하므로 별도 CI DB 비밀번호나 공유 DB가 필요 없습니다.

### 전자결재 상태 SQL 중복 실행

- 증상: 승인 한 번에 Controller와 DTO가 같은 결재선·문서 상태 갱신을 반복했습니다.
- 원인: 업무 흐름이 Controller와 DTO 양쪽에 분산되어 호출 순서를 한 곳에서 보장하지 못했습니다.
- 해결: `ApprovalService.processApproval` 한 곳에서 결재선 한 번, 문서 상태 집계 한 번만 실행하도록 변경했습니다.
- 재발 방지: 승인·반려 상태 전이를 통합 테스트로 고정했습니다.

### 한글 Java 소스의 깨진 인코딩

- 증상: `./mvnw clean test`에서 VO 파일에 `unmappable character` 컴파일 오류가 발생했습니다.
- 원인: 프로젝트 기본 인코딩은 UTF-8이지만 일부 VO 파일과 Eclipse 설정은 CP949/EUC-KR이었습니다.
- 해결: 7개 VO 파일을 CP949에서 UTF-8로 변환하고 `.settings/org.eclipse.core.resources.prefs`도 UTF-8로 통일했습니다.
- 확인: clean build에서 21개 Java 소스가 인코딩 오류 없이 컴파일되고 테스트 10개가 모두 통과했습니다.

### Git에 남아 있던 오래된 컴파일 클래스

- 증상: `target`을 Git 기준으로 원복한 뒤 `./mvnw test`를 실행하면 Maven은 컴파일할 것이 없다고 판단했지만, Spring은 제거된 `approvalDTO`와 이전 Controller 클래스를 읽어 ApplicationContext 생성에 실패했습니다.
- 원인: `.gitignore`에 `target/`이 있어도 과거에 커밋된 클래스·복사 리소스 등 생성 파일 23개는 계속 추적되고 있었습니다.
- 해결: 생성된 `target` 파일의 Git 추적을 해제했습니다. 이후 새 체크아웃에는 컴파일 클래스가 포함되지 않습니다.
- 확인: `./mvnw --batch-mode clean test`로 전체 소스를 다시 컴파일한 결과 10개 테스트가 모두 통과했습니다. CI의 `./mvnw --batch-mode test`도 빈 `target`에서 시작합니다.

### 변경 직후 성능 수치 변동

- 증상: 리팩터링 후 첫 측정에서 결재 목록 평균이 2.80ms에서 3.41ms로 증가했습니다.
- 원인 판단: 재시작 직후 JVM·JSP·DB 캐시 상태와 수 밀리초 구간의 측정 편차 영향이 큽니다.
- 처리: 첫 결과를 폐기하지 않고 그대로 보존한 뒤 동일 조건으로 2차 측정을 추가했습니다.
- 결론: 2차 측정은 평균 2.90ms, p95 3.71ms로 기준과 유사했습니다. 목록 SQL을 바꾼 작업이 아니므로 목록 응답 개선이라고 주장하지 않습니다.

## 7. 성능 측정과 개선 수치

조건은 로컬 서버, 로그인 세션 유지, 화면별 워밍업 5회 후 순차 50회입니다. 단위는 ms입니다.

| 결재 목록 | 성공 | 오류율 | 평균 | p50 | p95 | 최대 |
|---|---:|---:|---:|---:|---:|---:|
| Before | 50/50 | 0% | 2.80 | 2.68 | 3.75 | 4.40 |
| After 1차 | 50/50 | 0% | 3.41 | 3.31 | 4.04 | 5.43 |
| After 2차 | 50/50 | 0% | 2.90 | 2.79 | 3.71 | 4.36 |

Before 대비 안정화된 2차 결과는 평균 `+3.6%`, p95 `-1.1%`, 최대 `-0.9%`입니다. 이 차이는 수 밀리초 구간의 변동 범위로 보고, 사용자 체감 성능 개선 수치로 사용하지 않습니다.

코드 경로와 실행 SQL 수 기준으로는 다음 개선을 확인했습니다.

| 구간 | Before | After | 변화 |
|---|---:|---:|---:|
| 승인·반려 1회 UPDATE 수 | 5회 | 2회 | 60% 감소 |
| 상세 화면 조회 수 | 4회 | 3회 | 25% 감소 |
| 문서 상태 계산 시 결재선 상관 서브쿼리 | 최대 6회 | 그룹 집계 1회 | 반복 조회 제거 |

승인·반려 UPDATE 수는 이전 Controller와 DTO의 실제 호출 경로를 기준으로 계산했습니다. 현재 성능 스크립트는 목록 화면을 측정하므로, 60% 감소는 목록 응답 시간이 아니라 결재 처리 경로의 DB 왕복 감소 수치입니다. 운영 규모의 동시 요청 성능을 주장하려면 다음 단계에서 승인 API 부하 테스트와 DB 슬로우 쿼리·실행계획 측정을 별도로 추가해야 합니다.

## 8. 관련 파일

- 테스트: `src/test/java/com/erp/CoreFlowIntegrationTest.java`
- 테스트 설정: `src/test/resources/application-test.properties`
- CI: `.github/workflows/ci.yml`
- Controller: `src/main/java/com/erp/control/ApprovalController.java`
- Service: `src/main/java/com/erp/service/ApprovalService.java`
- Mapper: `src/main/java/com/erp/mapper/ApprovalMapper.java`
- SQL: `src/main/resources/mapper/approval_mapper.xml`
- 성능 원본: `performance/results/before-approval-service-refactor.md`
- 성능 원본: `performance/results/after-approval-service-refactor.md`
- 성능 원본: `performance/results/after-approval-service-refactor-run2.md`
