# 권한·공지사항·사원관리 리팩터링 설계 및 검증 기록

- 작업일: 2026-09-17
- 범위: URL 권한 정책, 결재 객체 권한 검증, 비밀번호 변경 테스트, 공지사항·사원관리 계층 분리
- 검증 명령: `./mvnw --batch-mode clean test`
- 검증 결과: 16개 테스트 성공, 실패 0, 오류 0, 건너뜀 0

## 1. 변경 구조

공지사항과 사원관리도 전자결재와 같은 구조로 통일했습니다.

```text
NoticeController        UserController
       ↓                       ↓
NoticeService           UserService
       ↓                       ↓
NoticeMapper            UserMapper
       ↓                       ↓
notice_mapper.xml       user_mapper.xml
```

기존 `noticeDTO`, `userDTO`는 제거했습니다. 각 Service가 업무 규칙과 트랜잭션을 담당하고 Mapper는 SQL 실행만 담당합니다. 구현체가 하나뿐인 Service 인터페이스와 Repository 계층은 추가하지 않았습니다.

## 2. 권한 정책

화면에서 버튼만 숨기는 방식은 권한 검사가 아니므로 Spring Security URL 정책과 Service 검사를 함께 적용했습니다.

| 기능 | 허용 사용자 | 검사 위치 |
|---|---|---|
| `/user/myinfo.do` | 로그인 사용자 본인 | Security, 세션 사용자 번호 |
| 그 외 `/user/**` | 관리자 | Security `ROLE_ADMIN`, UserService |
| 공지 조회·다운로드 | 로그인 사용자 | Security |
| 공지 작성·삭제 | 관리자 | Security `ROLE_ADMIN`, NoticeService |
| 결재 문서 조회·다운로드 | 작성자, 결재자, 최종 승인 후 일반 사용자 | ApprovalService 접근 조건 SQL |
| 결재 문서 수정·삭제 | 작성자 | ApprovalService 작성자·상태 조건 SQL |
| 승인·반려 | 해당 문서의 아직 처리하지 않은 결재자 | ApprovalService, 조건부 UPDATE |
| 비밀번호 변경 | 로그인 사용자 본인 | 세션 사용자 번호, 기존 비밀번호 검증 |

일반 사용자가 URL과 파라미터를 직접 조작해도 사원 목록·상세·등록·수정·삭제에 접근할 수 없습니다. 내 정보 URL에서는 요청 파라미터로 사원번호를 받지 않고 세션의 로그인 사용자 번호만 사용해 다른 사람 정보를 조회하는 IDOR 가능성을 제거했습니다.

사원 상세 조회 SQL에서는 `userpw` 컬럼을 제거했고 JSP의 비밀번호 입력 요소도 삭제했습니다. 따라서 BCrypt 해시도 브라우저 HTML에 노출되지 않습니다.

삭제 작업은 GET에서 POST로 바꾸고 CSRF 토큰을 포함하도록 수정했습니다.

## 3. 트랜잭션 경계

| Service 메서드 | 원자적으로 처리되는 작업 |
|---|---|
| `NoticeService.createNotice` | 공지 작성, 공지 대상 부서 일괄 등록 |
| `NoticeService.deleteNotice` | 관리자 확인, 공지와 연관 부서 삭제 |
| `UserService.createUser` | 관리자 확인, BCrypt 암호화, 직급 순서 계산, 사원 등록 |
| `UserService.updateUser` | 관리자 확인, 선택적 비밀번호 암호화, 사원 수정 |
| `UserService.deleteUser` | 관리자 확인, 단일 사원 삭제 |
| `UserService.deleteUsers` | 관리자 확인, 선택 사원 일괄삭제 |

공지 첨부파일은 DB 트랜잭션에 포함되지 않습니다. 파일 저장 후 DB 등록이 실패하면 Controller가 저장 파일을 제거하고, 공지 삭제 후 물리 파일 삭제가 실패하면 DB 결과는 유지한 채 고아 파일을 유지보수 대상으로 남깁니다.

## 4. 통합 테스트 확장

기존 10개 테스트에 다음 6개를 추가했습니다.

| 번호 | 검증 내용 |
|---:|---|
| 11 | 일반 사원의 사원관리 목록·상세·등록 접근은 403, 본인 정보는 200, 관리자 접근은 200 |
| 12 | 관리자가 사원을 등록·수정·삭제하고, 초기 비밀번호 BCrypt·최초 로그인·직급 순서 저장 확인 |
| 13 | 일반 사용자의 공지 작성·삭제는 403, 관리자는 공지와 대상 부서를 생성·삭제 |
| 14 | 다른 사용자의 결재 문서 수정 화면·수정 POST는 403이고 원본 제목 유지 |
| 15 | 결재와 무관한 사용자의 첨부 다운로드는 403, 결재자는 실제 파일 다운로드 성공 |
| 16 | 잘못된 기존 비밀번호는 거절하고, 변경 성공 후 BCrypt 저장·기존 비밀번호 로그인 실패·새 비밀번호 로그인 성공 |

모든 권한 검증은 실제 랜덤 포트 서버에 로그인한 뒤 세션 쿠키와 CSRF 토큰을 포함한 HTTP 요청으로 수행했습니다.

## 5. 트러블슈팅 기록

### 로그인 여부만 확인하던 사원관리

- 증상: 일반 로그인 사용자도 `/user/list.do`, `/user/view.do`, `/user/write.do`를 직접 호출할 수 있었습니다.
- 원인: JSP에서 버튼을 숨기거나 일부 POST Controller에서만 관리자 여부를 확인했습니다.
- 해결: `/user/myinfo.do`를 제외한 `/user/**`를 `ROLE_ADMIN`으로 제한하고 UserService에서도 관리자 여부를 다시 확인했습니다.
- 재발 방지: 일반 사용자와 관리자 세션을 각각 사용하는 통합 테스트를 추가했습니다.

### 암호화된 비밀번호의 화면 노출

- 증상: 사원 상세 SQL이 `userpw`를 조회하고 JSP가 해당 값을 password input의 value로 출력했습니다.
- 원인: 인증용 데이터와 사원관리 표시 데이터를 같은 조회 결과에 포함했습니다.
- 해결: 사원관리 Mapper 조회 컬럼에서 `userpw`를 제거하고 JSP 입력 요소도 삭제했습니다. 인증은 기존 LoginMapper만 비밀번호를 조회합니다.

### 화면 버튼에 의존한 공지 권한

- 증상: 공지 작성은 Controller에서 확인했지만 삭제는 로그인 여부만 확인했고 GET 요청으로 삭제할 수 있었습니다.
- 원인: 공지 업무 규칙이 Controller와 JSP에 분산됐습니다.
- 해결: 공지 작성·삭제를 Security와 NoticeService에서 관리자 전용으로 제한하고 삭제를 CSRF 보호 POST로 변경했습니다.

### 다중 SQL 중간 실패 가능성

- 증상: 공지 본문 저장 후 공지 대상 부서 저장 중 오류가 발생하면 일부 데이터만 남을 수 있었습니다. 사원 일괄삭제도 사용자 수만큼 개별 DELETE를 실행했습니다.
- 원인: 기존 DTO에 트랜잭션 경계가 없었습니다.
- 해결: Service에 `@Transactional`을 적용하고 공지 대상과 사원 삭제를 각각 하나의 다중 행 SQL로 변경했습니다.

### 사원번호 생성 반복 조회

- 증상: 현재 연도의 빈 사원번호를 찾기 위해 최대 100번 SELECT를 반복했습니다.
- 원인: Controller의 반복문이 후보 번호마다 DB를 조회했습니다.
- 해결: 해당 연도의 최댓값을 한 번 조회해 다음 번호를 계산합니다.
- 한계: 동시에 두 관리자가 등록하면 같은 번호를 계산할 수 있으며 DB PK가 중복 저장을 차단합니다. 실제 동시 등록 충돌이 관측되면 별도 시퀀스를 적용합니다.

## 6. 성능 측정

조건은 로컬 서버, 관리자 로그인 세션 유지, 화면별 워밍업 5회 후 순차 50회입니다. 단위는 ms입니다.

| 화면 | 구간 | 평균 | p50 | p95 | 최대 | 오류율 |
|---|---|---:|---:|---:|---:|---:|
| 공지 | Before | 3.90 | 3.84 | 5.31 | 6.35 | 0% |
| 공지 | After 1차 | 2.77 | 2.75 | 3.55 | 3.92 | 0% |
| 공지 | After 2차 | 2.21 | 2.12 | 2.83 | 3.29 | 0% |
| 사원 | Before | 2.86 | 2.74 | 3.54 | 3.86 | 0% |
| 사원 | After 1차 | 3.18 | 2.93 | 4.90 | 5.31 | 0% |
| 사원 | After 2차 | 2.75 | 2.56 | 3.80 | 4.09 | 0% |

Before 대비 2차 측정에서 공지 평균은 `-43.3%`, p95는 `-46.7%`로 관측됐습니다. 사원 평균은 `-3.8%`, p95는 `+7.3%`로 유의미한 변화가 없었습니다.

다만 같은 측정에서 변경하지 않은 결재 화면도 평균 4.20ms에서 2.36ms로 변했습니다. 따라서 공지 화면의 43.3% 전체를 SQL 변경의 인과 효과로 주장하지 않고 로컬 관측치로만 보관합니다.

코드 경로와 SQL 실행 수 기준으로 확정할 수 있는 개선은 다음과 같습니다.

| 구간 | Before | After | 변화 |
|---|---:|---:|---:|
| 공지 대상 3개 등록 | INSERT 3회 | 다중 VALUES INSERT 1회 | 66.7% 감소 |
| 사원 10명 일괄삭제 | DELETE 10회 | `IN` DELETE 1회 | 90% 감소 |
| 다음 사원번호 탐색 | SELECT 최대 100회 | 집계 SELECT 1회 | 최대 99% 감소 |
| 공지 목록 작성자 조회 | 행별 상관 서브쿼리 | 사용자 테이블 JOIN 1회 | 반복 상관 조회 제거 |

현재 로컬 데이터는 공지 1건, 사원 11명 수준이므로 운영 규모 처리량 수치로 확대 해석하지 않습니다. 데이터가 늘어나면 Testcontainers에 대량 픽스처를 넣고 동시 요청·실행계획을 함께 측정해야 합니다.

## 7. 관련 파일

- 권한 정책: `src/main/java/com/erp/config/SecurityConfig.java`
- 공지 Controller: `src/main/java/com/erp/control/NoticeController.java`
- 공지 Service: `src/main/java/com/erp/service/NoticeService.java`
- 공지 Mapper: `src/main/java/com/erp/mapper/NoticeMapper.java`
- 사원 Controller: `src/main/java/com/erp/control/UserController.java`
- 사원 Service: `src/main/java/com/erp/service/UserService.java`
- 사원 Mapper: `src/main/java/com/erp/mapper/UserMapper.java`
- 통합 테스트: `src/test/java/com/erp/CoreFlowIntegrationTest.java`
- Before 원본: `performance/results/before-access-domain-refactor.md`
- After 원본: `performance/results/after-access-domain-refactor.md`
- After 2차 원본: `performance/results/after-access-domain-refactor-run2.md`
