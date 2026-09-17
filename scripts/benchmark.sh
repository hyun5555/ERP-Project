#!/usr/bin/env bash
set -euo pipefail

label=${1:-after}
case "$label" in
	*[!A-Za-z0-9._-]*) echo "label은 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다." >&2; exit 1 ;;
esac

base_url=${BENCHMARK_BASE_URL:-http://localhost:8080/ERP}
usernum=${BENCHMARK_USER:-admin}
password=${BENCHMARK_PASSWORD:-1234}
requests=${BENCHMARK_REQUESTS:-50}
benchmark_tmp=$(mktemp -d /tmp/erp-benchmark.XXXXXX)
trap 'rm -rf "$benchmark_tmp"' EXIT

cookie_file="$benchmark_tmp/cookie.txt"
login_page="$benchmark_tmp/login.html"
curl -fsS -c "$cookie_file" "$base_url/login/login.do" -o "$login_page"

csrf_pair=$(sed -n 's/.*type="hidden" name="\([^"]*\)" value="\([^"]*\)".*/\1 \2/p' "$login_page" | head -n 1)
csrf_name=${csrf_pair%% *}
csrf_token=${csrf_pair#* }
if [ -z "$csrf_name" ] || [ "$csrf_name" = "$csrf_token" ]; then
	echo "로그인 화면에서 CSRF 토큰을 찾지 못했습니다." >&2
	exit 1
fi

login_result=$(curl -fsS -b "$cookie_file" -c "$cookie_file" \
	--data-urlencode "$csrf_name=$csrf_token" \
	--data-urlencode "usernum=$usernum" \
	--data-urlencode "userpw=$password" \
	"$base_url/login/login.do")
if [ "$login_result" != "OK" ]; then
	echo "벤치마크 로그인이 실패했습니다: $login_result" >&2
	exit 1
fi

mkdir -p performance/results
result_file="performance/results/$label.md"
{
	printf '# ERP 성능 측정: %s\n\n' "$label"
	printf -- '- 측정시각: %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
	printf -- '- 요청 방식: 워밍업 5회 후 순차 %s회\n' "$requests"
	printf -- '- 기준 URL: `%s`\n\n' "$base_url"
	printf '| 화면 | 성공/전체 | 오류율 | 평균(ms) | p50(ms) | p95(ms) | 최대(ms) |\n'
	printf '|---|---:|---:|---:|---:|---:|---:|\n'
} > "$result_file"

measure() {
	name=$1
	path=$2
	result="$benchmark_tmp/$name.txt"

	warmup=1
	while [ "$warmup" -le 5 ]; do
		curl -fsS -b "$cookie_file" -o /dev/null "$base_url$path"
		warmup=$((warmup + 1))
	done

	request_index=1
	while [ "$request_index" -le "$requests" ]; do
		curl -sS -b "$cookie_file" -o /dev/null -w '%{http_code} %{time_total}\n' \
			"$base_url$path" >> "$result"
		request_index=$((request_index + 1))
	done

	sort -nk2 "$result" > "$result.sorted"
	awk -v name="$name" '
		$1 == 200 { success++ }
		{ times[NR]=$2; sum+=$2 }
		END {
			p50=int((NR-1)*0.50)+1;
			p95=int((NR-1)*0.95)+1;
			printf "| %s | %d/%d | %.2f%% | %.2f | %.2f | %.2f | %.2f |\n", name, success, NR, (NR-success)*100/NR, sum*1000/NR, times[p50]*1000, times[p95]*1000, times[NR]*1000
		}' "$result.sorted" | tee -a "$result_file"
}

measure main /main.do
measure notice /notice/list.do
measure approval /approval/list.do
measure user /user/list.do

echo "결과: $result_file"
