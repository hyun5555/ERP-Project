# React 코드와 브라우저 검증 범위 리뷰

최신 후속 결과(2026-10-22): F1~F6의 암호 폼·대상 혼입·닫힌 메신저 읽음·대시보드 갱신·로그인 CSRF 처리를 보완하고 대표 브라우저 흐름 9개를 통과했다. [후속 구현·검증](data-usability-performance.md)을 따른다. F7/F8 키보드·모바일 메뉴는 남은 개선 후보다. F9 규모 경계는 사용자 선택의 1천 건에서 측정했으며 큰 규모 최적화는 요구하지 않는다. 아래는 과거 발견 기록이다.

2026-10-06 공유 작업 트리를 실제 Test Automation Engineer 역할로 읽기 전용 검토했다. 검토 대상은 `App`, API 헬퍼, 로그인·레이아웃·대시보드, 결재·공지·사원 화면, 메신저·AI 스트림, UI/CSS, Playwright와 JUnit wrapper다. 이 검토 담당자는 Maven, npm, Playwright, 환경 제어, 대량 데이터 생성, 브라우저·화면낭독기 실행을 하지 않았다. 소스·설치된 React 동작의 정적 확인과 주 에이전트가 순차 실행한 [관측 결과](review-evidence/results.json)·[환경 manifest](review-evidence/manifest.json)·[통합 검증 기록](stage5-6-design-review.md)을 대조했다. F1은 실행 재현된 미수정 오류이며 F2–F9는 정적 발견이다. 각 항목의 확인 수준을 넘어 실행 재현·수정 완료를 뜻하지 않는다. 다른 담당자의 통합 수정으로 줄 번호와 상태가 바뀔 수 있다.

## 1. 우선 검토할 기능 문제

### F1. 비밀번호가 바뀐 뒤 폼 초기화에서 오류가 발생한다

우선순위 P2. 위치: `frontend/src/user/UserPages.jsx:172–185`, 특히 179–181행.

트리거는 유효한 현재 비밀번호와 일치하는 새 비밀번호를 제출해 API가 성공하는 경우다. `await sendJson` 뒤 `setSuccess(result.message)`를 호출하고 이어서 `event.currentTarget.reset()`을 호출한다. 설치된 `react-dom-client.development.js`의 `executeDispatch`는 listener 호출 직후 `event.currentTarget=null`로 정리하며, async listener의 네트워크 완료를 기다리지 않는다. 따라서 재개 시점의 `currentTarget`은 form 참조로 사용할 수 없다. 이는 예전 event pooling 여부와 다른 문제다.

영향은 실제 암호 변경 성공 뒤 catch로 들어가 오류를 표시하고, 성공 문구와 오류가 함께 남으며 입력값도 초기화되지 않는 것이다. 같은 현재 비밀번호로 다시 시도하면 이미 변경된 암호와 불일치할 수 있다. API의 암호 변경 자체는 성공하는 경로이므로, 다른 대상에 쓰기를 요청할 수 있는 F2·F3보다 낮은 P2로 분류한다. 최소 수정은 `await` 전에 `const form=event.currentTarget`을 저장하고 그 참조로 FormData 생성과 `form.reset()`을 수행하는 것이다.

확인 수준: **native WAR의 Chromium 브라우저에서 실행 재현, 미수정**. [관측 probe](review-evidence/browser-probe.js.txt)는 비밀번호 POST 200과 `비밀번호가 변경되었습니다.` 안내를 확인한 뒤 alert의 `Cannot read properties of null (reading 'reset')`와 새 비밀번호 입력 잔류를 기록했다. [관측 결과](review-evidence/results.json)의 `passwordApiStatus=200`, `successVisible=true`, `passwordFieldRetained=true`가 일치한다. 변경된 비밀번호를 currentPassword로 사용한 원래 fixture 비밀번호 복원 요청도 200이었다. 이 관측은 오류를 찾아낸 결과이며 UI 정상 회귀 통과가 아니다. 현재 영구 브라우저 테스트는 성공 변경 경로를 검증하지 않는다. 수정 후에는 성공 문구·오류 없음·입력 초기화·새 비밀번호 로그인과 이전 비밀번호 거부를 한 흐름으로 확인한다. [React event 객체](https://react.dev/reference/react-dom/components/common#react-event-object).

### F2. 이전 조회가 늦게 끝나면 현재 검색·상세 상태를 덮을 수 있다

우선순위 P1 후보(상세의 쓰기 대상 혼동), P2(목록). 위치: `ApprovalPages.jsx:19–23,69–73,157–159`, `NoticePages.jsx:17–21,50`, `UserPages.jsx:21–35,88–91,124–130`, 호출 경계 `App.jsx:29–32,50–59`.

트리거는 같은 목록에서 검색·페이지·상태를 연속 변경하고 첫 요청이 두 번째보다 늦게 완료하는 경우다. 이전 요청의 `.then(setData)`와 `.catch(setError)`를 무효화하는 cleanup 또는 요청 식별자 검사가 없다. URL·필터는 B인데 결과·건수 또는 오류는 A가 될 수 있다. 삭제 후 `UserList.load()`도 당시 route를 캡처하므로 처리 중 필터 변경과 함께 확인할 필요가 있다.

상세에서는 SPA의 history 메뉴로 두 상세 주소 사이를 직접 이동해 같은 컴포넌트가 유지될 때가 조건이다. `ApprovalDetail`과 `NoticeDetail`은 ID 변경 직후 기존 data/notice를 비우지 않는다. 특히 결재 화면에는 이전 문서와 canApprove가 보이는 동안 새 URL의 `id`를 `submitAction`에 사용하는 구간이 있다. 새 문서도 본인에게 승인 가능한 문서라면 다른 문서를 보며 그 문서에 결재를 요청할 수 있다. 서버 권한 검사 우회라는 뜻은 아니다.

최소 수정은 effect cleanup의 active/ignore 표지로 이전 성공·실패 응답을 무시하고, 문서 ID 변경 시 데이터·오류·의견을 초기화하는 것이다. 식별자로 해당 상세를 remount하는 방식도 가능하다. 조회 경합 처리와 폼 입력 초기화(F3)는 각각 확인해야 한다.

확인 수준: 역순 응답과 상세 직접 history 이동은 실행하지 않았다. 소스상 상태 갱신 보호·초기화 부재를 확인했다. 후속 브라우저 검증은 조건 신호로 요청 A를 보류하고 B를 먼저 완료한 뒤 A를 해제해 URL·필터·표시 ID·쓰기 ID가 모두 B인지 확인한다. 임의 sleep을 쓰지 않는다. [React의 effect 조회 경합 처리](https://react.dev/reference/react/useEffect#fetching-data-with-effects).

### F3. 같은 수정 컴포넌트의 대상 ID가 바뀌어도 기존 입력값이 남을 수 있다

우선순위 P1 후보. 위치: `UserPages.jsx:124–130,140,155–160`, `ApprovalPages.jsx:109–117,137–142`, 대상 식별자 key가 없는 호출 `App.jsx:76,84`.

트리거는 같은 SPA 문서 안에서 history 메뉴로 두 사원 수정 또는 두 결재 수정 주소 사이를 직접 이동하는 경우다. effect로 새 사용자/문서 자료를 읽어도 대부분의 입력은 `defaultValue`/`defaultChecked`다. 이는 기존 input의 편집값을 새 대상 자료로 동기화하는 제어 값이 아니다. 기존 입력을 수정한 상태라면 그 값이 남는 조건은 더 명확하다.

영향은 대상 URL·hidden approval_no·PUT 경로는 B인데 A에서 남은 이름·본문·부서 등의 입력을 B에 저장할 수 있다는 것이다. F2의 응답 순서 보호만 적용해도 이 문제는 남을 수 있다. 최소 수정은 수정 대상 ID로 form/수정 컴포넌트를 remount해 새 초기값을 적용하는 것이다. 별도 폼 프레임워크를 추가할 필요는 없다.

확인 수준: key와 uncontrolled 초기값의 정적 확인. 사용자 데이터 덮어쓰기를 실행하지 않았다. 후속 검증은 서로 다른 초기값의 A/B를 만들고 A를 편집한 뒤 history로 B를 선택해, B의 값과 제출 대상이 일치하는지 확인한다. [React의 key를 이용한 폼 초기화](https://react.dev/learn/preserving-and-resetting-state#resetting-a-form-with-a-key).

### F4. 닫힌 메신저 패널의 선택된 방에도 읽음을 기록한다

우선순위 P2. 위치: `AppLayout.jsx:33`, `Messenger.jsx:23–30,48–54,63–69,121–123`.

트리거는 방 A를 연 뒤 패널을 닫거나 AI 패널을 열고, 다른 사용자가 A에 메시지를 보내는 경우다. Messenger는 항상 mount되어 있고, 닫기는 `open=false`로 aside만 제거한다. selectedRoom은 남는다. MESSAGE callback은 선택 방 일치만 확인하고 open 여부 없이 markRead를 수행한다. sync 후 과거 기록 로딩에서도 markRead가 호출된다.

영향은 대화가 화면에 표시되지 않아도 DB에 읽음 시각을 저장하고 발신자에게 읽음 영수증을 보내는 것이다. 최소 수정은 공통 읽음 처리에서 패널 표시와 선택 방을 함께 확인하고, 패널을 다시 열 때 해당 방의 미확인 메시지를 처리하는 것이다. effect에서 초기 open 값을 캡처한 채 단순 `if(open)`만 추가하면 안 된다.

확인 수준: 닫기·mount·수신 callback의 정적 경로 확인. 실제 두 브라우저의 영수증 재현은 하지 않았다. 후속 검증은 A를 열었다가 닫은 수신자에게 메시지를 보내 미읽음 유지, 다시 A를 열면 읽음 변경을 확인한다. 별도의 native 로그아웃 probe에서는 소켓 close 1008과 이후 메시지 미저장이 확인됐으며, 기존 소켓이 로그아웃 뒤 송신 가능하다는 초기 가설은 철회됐다. 이는 패널이 닫힌 상태의 읽음 처리 검증을 대신하지 않는다.

### F5. SPA 처리 후 대시보드·사이드바의 현황이 갱신되지 않는다

우선순위 P2. 위치: `App.jsx:46–48,69–71`, `AppLayout.jsx:41–51`, `ApprovalPages.jsx:167–169`, `NoticePages.jsx:56–57`.

트리거는 페이지를 다시 읽지 않고 결재를 처리하거나 메인 공지를 삭제한 뒤 홈·다른 업무 화면으로 이동하는 경우다. dashboard 조회는 session.authenticated 변경만 의존하고, 결재/공지 처리 이후 새 현황을 읽는 호출이 없다. 일반 SPA navigate도 dashboard를 갱신하지 않는다.

영향은 변경 전 건수나 삭제된 공지의 제목·링크가 같은 세션의 홈과 사이드바에 남을 수 있다는 것이다. native 작성 폼의 전체 탐색은 별개다. 최소 수정은 업무 변경 성공 또는 홈 진입이라는 명확한 경계에서 기존 dashboard 조회를 다시 사용해 state를 갱신하는 것이다. 주기 polling부터 추가하지 않는다.

확인 수준: 상태 의존성과 호출자 정적 확인. 후속 검증은 실제 API 현황과 처리 후 SPA 홈·사이드바의 숫자·공지 항목이 일치하는지 대조한다.

### F6. 로그인 화면의 CSRF가 만료되면 계속 비밀번호 오류처럼 보인다

우선순위 P2. 위치: `PublicPages.jsx:58–76`, 초기 CSRF를 한 번 가져오는 `App.jsx:35–44`.

트리거는 로그인 화면을 열린 상태로 두어 그 세션이 만료되거나, 같은 브라우저의 다른 탭에서 로그인/로그아웃으로 세션·CSRF가 바뀐 뒤 기존 화면에서 제출하는 경우다. raw fetch는 403과 실제 자격 증명 401을 구분하지 않고 결과가 OK/CHANGE가 아니면 같은 오류를 보여 준다. 화면의 csrf prop을 새로 가져오는 경로도 없다.

영향은 올바른 계정을 입력해도 기존 토큰을 다시 보내 403을 반복할 수 있고 원인 안내도 틀리는 것이다. 최소 수정은 로그인 POST의 403을 구분해 공개 session API에서 토큰을 다시 준비하거나 로그인 화면을 새로 로드하고, 비밀번호 오류와 구분된 안내를 제공하는 것이다. 일반 로그인 실패 401을 자동 로그인 이동으로 처리하지 않는다.

확인 수준: 응답 분기·토큰 생명주기 정적 확인. 실제 만료된 로그인 폼은 기존 보호 화면 만료 테스트와 다른 사례이며 실행하지 않았다.

## 2. 키보드 접근과 데이터 규모의 경계

### F7. 사원 상세 진입이 마우스 행 클릭에만 연결돼 있다

우선순위 P2. 위치: `UserPages.jsx:76`, `user.css:8–9`.

관리자 목록의 tr에는 onClick이 있지만 사원명은 strong이며, 상세 진입용 anchor/button 또는 키보드 활성화가 없다. 키보드로 체크박스를 선택할 수 있어도 해당 사원 상세·수정으로 진입하는 동등한 조작이 제공되지 않는다. 최소 수정은 사원명에 실제 상세 href를 가진 링크를 두는 것이다. tr에 임의 role·복잡한 key handler를 붙일 필요는 없다.

확인 수준: HTML 의미·이벤트 정적 확인. 실제 키보드·화면낭독기 테스트는 하지 않았다. 후속 검증은 Tab으로 사원 상세 링크를 찾아 Enter로 열고 이름·사원번호를 확인한다.

### F8. 모바일 닫힌 메뉴가 화면 밖에서도 포커스 대상이다

우선순위 P2. 위치: `AppLayout.jsx:22–26,60–80`, `layout/sidebar.css:105–108`.

1080px 이하에서 닫힌 sidebar는 transform으로 화면 밖에 이동한다. 내부 링크와 폼은 여전히 렌더되고, hidden/visibility/inert 처리가 없다. 따라서 키보드 순서에는 보이지 않는 메뉴 항목이 남는다. 최소 수정은 모바일 닫힌 상태를 시각뿐 아니라 포커스·접근성 트리에도 숨기는 것이다. 메뉴를 열고 닫을 때 초점 이동·복귀와 Escape도 실제 브라우저로 확인한다. 데스크톱 메뉴까지 숨기지 않도록 breakpoint와 open 상태를 같이 다룬다.

확인 수준: CSS·DOM 정적 확인. 포커스 이동·화면낭독기 발화를 실행하지 않았다. 기존 CSS에 입력 focus 표시와 reduced-motion 처리가 있다는 사실만으로 전체 접근성 통과를 선언하지 않는다.

### F9. 페이지 버튼과 채팅 DOM이 전체 데이터 크기에 비례한다

우선순위 P2, **측정할 규모 경계**. 위치: `ApprovalPages.jsx:218–219`, `NoticePages.jsx:39`, `UserPages.jsx:77`, `Messenger.jsx:21–24,129`, `messenger/messages.js:1–7`.

totalPages만큼 버튼을 생성하므로 해당 조회 결과가 10만 건이고 페이지당 10이면 1만 버튼이 된다. 이는 산술·소스상의 DOM 개수이며 지연 측정값은 아니다. 채팅 기록은 전량을 상태에 합치고 정렬해 전부 표시한다. MESSAGE당 대화방 조회와 읽음 뒤 대화방 재조회도 별도 비용 경계다.

영향은 큰 데이터에서 DOM 수·응답 바이트·정렬·렌더 작업이 함께 늘어나는 것이다. 지금 느린 병목으로 확정하지 않는다. 단계 5에서 DOM 수, observer 원문, 실제 API 요청 수와 준비 시간을 먼저 기록한다. 필요가 재현되면 현재 페이지 주변 버튼과 메시지 커서/최근 기록부터 적용하고, 기존 접근 권한·순서·재접속 복구·읽음 범위를 유지한다. 가상 목록이나 새 캐시 의존성부터 추가하지 않는다.

확인 수준: 계산과 호출 경로 정적 확인. 대량 fixture·longtask·반응시간·SQL 실행 계획은 수집하지 않았다.

## 3. 기존 기능 검증이 보장하는 범위

`frontend/e2e/core-flow.spec.js`의 다섯 흐름은 UI 로그인·기안·선택 순서 승인·결재 첨부 바이트와 실제 idle 만료 후 읽기·JSON 쓰기·승인 POST·multipart 쓰기를 검증한다. `playwright.config.js`는 단일 Chromium, workers 1, retries 0이며 실패 trace/screenshot/video를 남긴다. `BrowserFlowIntegrationTest`는 격리 MySQL·임시 업로드·전용 계정·random-port 서버를 준비하고 테스트 전용 native idle probe를 import한다. `.github/workflows/ci.yml`은 프론트 검증·빌드와 Chromium 설치 후 Maven을 실행한다.

이를 성공 비밀번호 변경 UI의 정상 동작, 사원 CRUD·선택 삭제, 공지 작성·다운로드, 역순 응답·상세 history 전환, 메신저 UI 읽음·닫힘·키보드, 모바일 메뉴, 실제 모델 답변의 브라우저 표시, 모든 화면 접근성·대량 성능까지 확인한 결과로 확대하지 않는다. 특히 만료 비밀번호 요청은 API 성공 전에 거부되므로 F1의 성공 후 폼 reset 오류를 검출하지 못한다. 성공 비밀번호 변경 오류는 별도 임시 관측으로 확인했다.

기존 Node 검증은 메시지 병합과 AI SSE 형식·종결을 대상으로 한다. 서버 WebSocket 회귀와 실제 메신저 DOM 조작도 서로 다른 경계다. [관측 결과](review-evidence/results.json)와 [통합 검증 기록](stage5-6-design-review.md)에서 다음 실행 범위를 확인했다. 실행 주체는 주 에이전트이며 이 문서 담당자가 다시 실행한 결과가 아니다.

| 경계 | 실제 결과 | 해석과 한계 |
|---|---|---|
| 기존 회귀·패키징 | `./mvnw --batch-mode verify` 57개 중 56개 통과, 실제 모델 평가 1개 생략, 실패·오류 0; Node 7개·Vite build·WAR packaging 통과 | Testcontainers 회귀는 native-only 요청 이전의 별도 Docker 범위. 원격 CI 실행 결과가 아님 |
| native 원본·새 복원본 | UI 로그인 → 기안 작성 → 순차 승인 → 결재 첨부 다운로드 바이트 각각 통과 | [manifest](review-evidence/manifest.json)의 source/restore core flow 값과 [브라우저 실행 대상](review-evidence/native-browser-context.json)으로 범위 식별. native 패키지에서는 idle 전용 endpoint가 없어 만료 4개를 재실행하지 않음 |
| 비밀번호 성공 경로 | POST 200·성공 안내 뒤 reset null 오류와 입력 잔류 재현 | F1의 실제 결함 관측. 변경된 암호는 원래 fixture 값으로 복원했으며 UI 수정은 하지 않음 |
| native WebSocket 로그아웃 | logout 302, HTTP 인증 false, 소켓 close 1008, 이후 고유 메시지 미저장 | [probe](review-evidence/native-websocket-probe.js.txt)는 CONNECT·sync 준비 후 실제 HTTP logout을 수행하고 별도 인증 context에서 저장 이력을 조회함. 패널 읽음·장시간 idle·강등/퇴직 검증으로 확대하지 않음 |

임시 관측 probe는 원래 테스트 파일에서 제거됐고 [manifest](review-evidence/manifest.json)에 원본 E2E spec 복구가 기록돼 있다. 보관된 `.txt`는 관측 절차이며 영구 정상 회귀 테스트가 아니다. 전체 프로젝트가 정상이라는 판정에 사용하지 않는다.

## 4. 단계 5 성능 설계 독립 검토

`performance-measurement-design.md:76–97,135–150`은 기능용 spec 실행시간을 성능 지표로 사용하지 않고, production 번들의 별도 브라우저 측정과 동일 조건·원문 보관을 정의했다. LCP를 문서 최초 입력 이전 후보로 한정하고, CLS를 session window로 계산하며, event 입력 반응과 API 완료 뒤 업무 결과 표시를 구분한다. 고정 자동 클릭 결과를 운영 INP·현장 p75로 부르지 않는다. 미관측/미지원 값을 0 대신 null로 보존하며, UI 중복 제거 이전의 callback도 메신저 전달 결과에 남기므로 측정 경계가 분명하다. 최신 설계에는 API에 대응하는 대상 DOM 완료와 rAF/실제 paint 구분도 반영돼 있다.

구현 시 다음 완료 신호와 조건을 실제 원문에 남긴다.

1. 정적 h1만으로 업무 준비를 판정하지 않는다. 대상 ID·건수·상태·결과 행이 해당 API 응답과 일치하고 로딩이 끝난 시점을 사용한다. 현재 기능용 openWorkspace가 기다리는 dashboard/rooms/contacts만으로 공지·결재 검색 완료를 판정하지 않는다.
2. frame callback 시각을 관측 paint 시각이라고 명명하지 않는다. DOM 결과 확인 시각, 렌더 프레임 callback 시각, 실제 PerformanceObserver entry를 구분한다. requestAnimationFrame callback은 repaint 전에 호출된다. [requestAnimationFrame 문서](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame).
3. CLS 방문 길이, 관측 중 수행한 입력, LCP 종료, cold/warm context 생성 순서를 실제 실행 manifest에 넣는다. 새 context는 DB·OS 캐시 초기화를 의미하지 않는다.
4. 기능상 잘못된 결과가 빠르게 보인 표본을 정상 성능 표본으로 세지 않는다. F1–F6 같은 기능 문제의 재현·정정과 정확한 결과 검증이 우선이다.
5. 현재 Testcontainers 기능 실행과 별도 native 패키지 실행의 결과를 구분한다. 최신 실행 범위는 WAR 앱, MySQL, 업로드 디렉터리를 각각 준비해 초기화·재기동·복구하는 구성이다. 실제 패키지·DB·업로드 경로와 실행 manifest가 일치해야 해당 구성의 성능 결과로 기록할 수 있다.

독립 검토 결론은 **설계의 측정 구분과 완료 기준이 적절하다**는 것이다. 설계 작성, 기존 5개 기능 흐름의 통과 또는 반복 실행시간만으로 단계 5 성능 측정 완료를 선언할 수 없다. 별도 browser 원문, 환경 manifest, 실제 반복 결과와 실패 분모가 생성되고 재계산 가능해야 실행 완료다.

## 5. native WAR·MySQL·업로드 분리 실행과 검증 연결

최신 사용자 지시에 따라 단계 6의 실제 수행 범위는 Docker Compose에서 native WAR 앱·MySQL·업로드 디렉터리 분리 실행으로 변경됐다. 주 에이전트가 전용 환경에서 초기화·기동·재기동·백업·새 복원을 수행했고, [통합 검증 기록](stage5-6-design-review.md)에 데이터·스키마·첨부 정합성과 복원 후 동시 번호 발급 결과를 남겼다. 이 문서 담당자는 환경을 다시 제어하지 않고 기록을 대조했다. 현재 실행의 완료 조건에 프록시나 HTTPS를 포함하지 않는다.

기존 다섯 흐름의 Testcontainers 검증 결과는 그 격리 서버와 DB 경계에서 유효하다. JUnit wrapper는 서버·DB를 직접 만들고 테스트 전용 idle endpoint를 import하므로, URL만 native 패키지 주소로 바꿔 만료 흐름까지 동일하게 검증할 수는 없다. 새 패키지 브라우저 검증에서는 전용 계정·설정과 실제 세션으로 UI 로그인 → 기안 작성 → 선택 순서 승인 → 첨부 다운로드 바이트를 원본·새 복원본에서 각각 통과했다. 성공 비밀번호 변경의 별도 probe는 F1을 실제 관측했고 암호를 원복했다. 임시 관측 코드는 제거했으며 운영 코드에 전용 만료 endpoint를 추가하지 않았다.

파일·데이터 보존과 복원은 브라우저 성공만으로 완료가 아니다. 앱 재기동, MySQL 데이터 복구, 첨부 복구·체크섬, 문서와 파일의 정합성은 [업무·복구 검증](review-evidence/native-recovery.json)과 [최종 새 복원 결과](review-evidence/native-final-restore.json)의 별도 경계다. native 핵심 UI 통과, 복구 통과, F1 오류 재현, F2–F9 정적 발견을 각각 구분한다.

native HTTP logout 후 소켓 종료·미저장은 확인했으므로 해당 취약점 가설은 유지하지 않는다. 운영 후속 범위에서는 실제 idle 정책에 따른 세션 만료와 기존 소켓 권한, HTTPS 쿠키 설정·로그인 redirect와 프록시를 통과하는 다운로드·WebSocket·SSE를 별도로 확인한다. 현재 localhost native 실행을 그 검증의 근거로 확대하지 않는다. 이 리뷰는 현재 프로젝트 전체가 완전히 정상이라는 판정이 아니라, 확인한 경계와 후속 검증의 근거를 남긴다.
