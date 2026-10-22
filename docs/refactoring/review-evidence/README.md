# 실행 근거와 재현 범위

최신 근거(2026-10-22): `dependencies-20261022.json`은 High 2→0과 lockfile 재설치 결과, `data-usability-20261022.json`은 전체 82개 중 80개 통과·선택 2개 생략·브라우저 9개 및 기존 native 데이터 보존 결과다. [1천 건 성능 원문·독립 재계산](../../../performance/results/20261022T103945Z-2fe863/README.md)은 별도 실행 기록이다. 추가 규모는 사용자 지시로 중단·평가 제외했다.

`manifest.json`과 `results.json`은 2026-10-06의 dirty working tree에서 실제 수행한 과거 결과다. `manifest.json`은 당시 변경본·두 관리 스크립트·schema·WAR의 해시이며 10월 22일 변경본을 뜻하지 않는다. `results.json`은 기존 회귀와 추가 관측을 구분한다. 관측 probe가 통과한 것은 재현 절차가 동작했다는 뜻이며, 취약하거나 잘못된 동작의 정상 판정이 아니다.

2026-10-22 P1 수정의 최신 근거는 `p1-20261022.json`이다. Maven 71개 중 70개 통과·선택 모델 평가 1개 생략, Node 7개, 실제 브라우저 5개 흐름, 새 소스·WAR 해시와 기존 native 행·첨부 보존 및 독립 복원 결과를 기록했다. 아래 과거 관측 probe를 이번에 다시 실행하지 않았다. 설계와 전환 절차는 [P1 구현 기록](../p1-session-approval-history.md)을 따른다. 기존 의존성 High 2건, migration 중간 중단·전체 롤백의 실행 미검증도 구분했다.

native 업무·보호·복구는 프로젝트 루트에서 다음 명령으로 재현한다. 전용 source의 개발 fixture를 작성하고 새 복원본에 두 사원을 생성하므로 실제 업무 환경에는 사용하지 않는다. 기존 DB·복원본은 덮어쓰지 않는다.

```bash
python3 scripts/dev-environment.py status
python3 scripts/dev-environment.py check
python3 scripts/verify-dev-recovery.py
```

`native-recovery.json`은 첨부·다운로드 권한·재기동·동시 번호·보호 조건의 결과다. `native-client-guard.json`은 mysql 로컬 명령 차단과 sentinel 미생성을 기록한다. `native-final-restore.json`은 최종 스크립트로 새 복원본의 schema/행/파일 일치·원본 유지·임시 계정 제거를 확인하고 그 복원본을 정상 종료한 기록이다. 이 소량 한 번의 시간은 성능 반복 표본이나 운영 복구 시간 목표가 아니다. 검증 CLI 자체는 새 복원본을 실행 상태로 남기며, 이번 최종 통합 작업에서는 JSON의 `restore_stopped_after_verification`대로 종료했다.

`core-probe.java.txt`는 기존 `CoreFlowIntegrationTest`의 helper·격리 Testcontainers fixture를 사용하는 임시 메서드 본문이다. 사용자 native-only 선택 전에 실행했고 실제 HTTP·DB 관측을 `results.json`에 남겼다. `websocket-probe.java.txt`의 초기 로그아웃 취약점 가설은 Connection closed 이후 철회했다. 실제 browser의 `native-websocket-probe.js.txt`가 logout→인증 false→close 1008→미저장을 확인했다.

`browser-probe.js.txt`와 `native-websocket-probe.js.txt`는 `frontend/e2e/core-flow.spec.js`의 기존 helper에 임시로 추가하여 실행한 본문이다. `native-browser-context.json`은 실행 당시 fixture ID suffix·source/restore URL을 남긴다. 원본·복원본 핵심 UI는 기존 test를 그대로 실행했다. 비밀번호 probe는 실제 변경 성공 뒤 reset 오류를 관측하고 fixture 암호를 원래 값으로 되돌렸다. 서버·fixture는 실행마다 다시 준비해야 하며 과거 복원 URL을 현재 실행 URL로 간주하지 않는다.

임시 probe는 실행 후 본래 테스트 소스에서 제거했고 원본 SHA-256 일치를 확인했다. 반복 성능 실측·다른 머신·원격 CI·운영 HTTPS는 이 기록에 포함되지 않는다. 발견·수정·미수정 판정은 [통합 검토](../stage5-6-design-review.md), [보안 리뷰](../code-review-security.md), [프런트 리뷰](../code-review-frontend.md)를 따른다.
