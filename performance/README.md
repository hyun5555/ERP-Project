# 성능 측정 방법

VS Code에서 ERP를 실행한 뒤 같은 장비, 같은 계정, 같은 데이터로 측정합니다.

```bash
./scripts/benchmark.sh before
./scripts/benchmark.sh after-security-login-refactor
```

기본값은 각 화면 워밍업 5회 후 순차 50회입니다. 조건을 바꿀 때는 결과 문서에 반드시 함께 기록합니다.

```bash
BENCHMARK_REQUESTS=100 ./scripts/benchmark.sh after-100-requests
```

비교 지표는 성공률, 평균, p50, p95, 최대 응답시간입니다. 서로 다른 조건에서 나온 수치는 개선 수치로 사용하지 않습니다.
