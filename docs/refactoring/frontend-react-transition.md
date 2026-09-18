# 프론트엔드 React/Vite 전환 기록

## 목표

기존 `.do` URL과 Spring Security 권한은 유지하면서 모든 업무 화면을 하나의 React 레이아웃으로 전환했습니다. Bootstrap·jQuery 화면을 제거하고 반응형 사이드바, 다크 모드, 사내 메신저, AI 챗봇을 공통으로 제공합니다.

## 현재 구조

```text
브라우저
  └─ React 19 / Vite 8
       ├─ layout       공통 셸·랜딩·로그인·대시보드
       ├─ approval     결재 목록·수신·작성·수정·상세
       ├─ notice       공지 목록·작성·상세
       ├─ user         사원 목록·상세·등록·수정·내 정보·비밀번호
       ├─ messenger    1:1 사내 메신저
       └─ ai           AI 챗봇 패널
          ↓ /api/* JSON
Spring Boot
  ├─ DashboardApiController
  ├─ ApprovalApiController
  ├─ NoticeApiController
  └─ UserApiController
       ↓
기존 Service → Mapper → MySQL
```

`App.jsx`는 URL 판별과 모듈 조합만 담당합니다. 기존 `.do` 주소의 GET 요청은 얇은 `app.jsp`를 반환하므로 새로고침과 URL 직접 접근이 가능하며, 업무 데이터는 도메인별 JSON API에서 읽습니다.

## 권한 경계

| API | 권한 |
|---|---|
| `GET /api/session` | 비로그인 허용 |
| `GET /api/dashboard` | 로그인 사용자 |
| `/api/approvals/**`, `/api/notices/**` | 로그인 + Service의 객체 권한 |
| `GET /api/users/me`, `POST /api/users/me/password` | 로그인 사용자 본인 |
| 나머지 `/api/users/**` | 관리자 |

비밀번호 해시는 사용자 JSON 응답에 포함하지 않습니다. 비밀번호 변경은 현재 비밀번호 확인 후 BCrypt로 저장합니다.

## 레거시 제거

- 업무 화면 JSP 및 중복 include 19개 제거
- Bootstrap 및 jQuery 기반 CSS·JavaScript 제거
- 서버 렌더링은 `app.jsp`와 로그인 폴백 `login.jsp`만 유지
- CSS Grid/Flexbox와 미디어 쿼리로 모바일 반응형 처리
- 모든 인증 화면에서 동일한 사이드바·다크 모드·메신저·챗봇 사용

## 성능 측정

초기 React 전환 당시 동일 장비·데이터·계정에서 워밍업 5회 후 순차 50회 요청했습니다.

| 화면 | 평균 Before → After | 평균 변화 | p95 Before → After | p95 변화 |
|---|---:|---:|---:|---:|
| 메인 | 2.78ms → 1.09ms | -60.8% | 3.78ms → 1.48ms | -60.8% |
| 결재 목록 | 3.00ms → 0.88ms | -70.7% | 3.92ms → 1.08ms | -72.4% |

이는 데이터가 포함된 JSP 대신 React 셸 HTML을 반환하는 서버 응답 시간입니다. JSON API와 브라우저 렌더링을 포함한 체감 성능 수치로 사용하지 않습니다.

현재 프로덕션 빌드는 JavaScript 308.18KB(gzip 90.87KB), CSS 49.52KB(gzip 10.46KB)입니다.

## 트러블슈팅

### 일부 업무 화면에서 공통 UI가 달라짐

원인은 CSS가 아니라 JSP와 React가 서로 다른 DOM·JavaScript 컴포넌트를 렌더링한 것이었습니다. 모든 업무 GET 요청을 동일한 React 셸로 연결해 공통 UI를 한 번만 렌더링하도록 해결했습니다.

### React 전환 후 CSRF 테스트 실패

기존 테스트가 숨은 `_csrf` 입력값을 읽고 있었습니다. React 요청용 meta 태그와 기존 폼 호환용 hidden input을 함께 제공해 회귀 없이 전환했습니다.

### 사원관리 API 권한 분리

`/api/users/**` 전체를 관리자 전용으로 묶으면 일반 사용자의 내 정보와 비밀번호 변경도 차단됐습니다. 구체적인 본인 API 규칙을 먼저 선언하고 나머지 사용자 API에 관리자 권한을 적용했습니다.

## 검증 결과

- `npm run build`: 성공
- `./mvnw --batch-mode -DskipTests compile`: 성공
- `./mvnw --batch-mode test`: 18개 성공, 실패 0, 오류 0
- 일반 사용자 본인 정보·비밀번호 변경과 관리자 사원 CRUD 권한을 통합 테스트로 확인
- 기존 `.do` 직접 접근과 동일 React 셸 반환을 통합 테스트로 확인
