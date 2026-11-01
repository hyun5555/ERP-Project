# 2026-11-01 최종 실제 AI 모델 비교

서버의 누락 출처 보완과 숫자 제목 검색 수정을 포함한 AiService로 질문 8개 × 2회 × 2모델을 실행했다. 전용 Testcontainers MySQL을 사용하며 기존 native DB에는 요청하지 않았다.

Apple M5 · RAM 32 GiB · macOS 26.5.2 arm64 · Java 21.0.12.1 · Ollama 0.33.0. think=false, temperature=0.2, 제한 60초, 모델별 워밍업 1회다. 모델 digest·양자화·질문·프롬프트 SHA-256은 원문 metadata/trial에 있다.

| 모델 | 생성 실패 | 앱 답변 사실·출처 검사 | 직접 안내 | 첫 토큰 p50 / 표본 p95 | 생성 전체 p50 / 표본 p95 |
|---|---:|---:|---:|---:|---:|
| qwen3.5:2b | 0/10 | 10/10 | 6/6 | 35 / 221 ms | 811 / 2,012 ms |
| qwen3.5:9b | 0/10 | 10/10 | 6/6 | 668 / 951 ms | 1,934 / 5,834 ms |

[집계](summary.json)와 [원문 32건](responses.jsonl)을 대조했다. 교육 일정·장소와 결재 금액·제목·출처가 지정 조건과 일치했다. 권한 밖 공지·타인/순서 전 결재나 참고자료의 악성 명령은 이 답변들에 포함되지 않았다. 이 32건에서는 조회 자료의 출처가 이미 표시돼 서버의 누락 출처 footer는 추가되지 않았다.

그러나 [출처 보완 전 비교](../ai-quality-20261101-final/README.md)의 2B 답변 1건은 같은 요약을 반복했고, 별도 [native 화면 질문](../../../docs/refactoring/review-evidence/ai-source-omission-20261101.json)에서는 사실은 맞지만 문서 번호를 생략했다. 이를 정상 모델 완료 뒤 빠진 조회 자료의 유형·번호·제목만 추가하도록 보완했다. 공백·Markdown 차이는 같은 출처로 보고 중복하지 않으며, 실패 응답에는 추가하지 않는다. 조회 자료 표시는 모델 본문의 모든 주장을 검증했다는 뜻이 아니다.

인용 제목 속 숫자와 제목 뒤 `3줄로` 숫자를 ID로 읽지 않는 경계는 후속 MySQL·브라우저 회귀로 확인했다. 최신 전체 결과와 실제 native·SSE·Chromium 5질문은 [마감 검증](../../../docs/refactoring/portfolio-readiness.md)에 연결한다.

기본 모델은 지연이 짧은 2B를 유지한다. 표의 시간은 성공한 워밍업 이후 LocalLlmClient 생성 표본이다. 각 trial의 wallDurationMs에는 AiService SQL 등이 포함되지만 ERP HTTP/SSE·화면 표시는 제외한다. 표본 10개의 p95와 지정 키워드 검사를 운영 보장·일반 정확도로 확대하지 않는다.

재현은 [모델 비교 명령](../../../docs/refactoring/ai-quality-browser-flows.md#실제-모델-비교-재현)을 사용하고 출력 폴더를 새로 지정한다.
