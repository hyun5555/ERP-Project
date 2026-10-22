#!/usr/bin/env python3
"""Opt-in native performance fixtures and reproducible measurements; never seeds an existing root."""
import argparse
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import platform
import secrets
import subprocess
from datetime import datetime, timedelta, timezone

PROJECT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("native_dev", PROJECT / "scripts/dev-environment.py")
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)
SEED = 20261006
PASSWORD = "performance-local-only"


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def memory_size():
    try:
        if platform.system() == "Darwin":
            return int(subprocess.check_output(["sysctl", "-n", "hw.memsize"], text=True, timeout=5)), None
        return os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE"), None
    except (ValueError, OSError, subprocess.SubprocessError):
        return None, "Physical memory query unsupported or unavailable"


def validate_socket_path(root):
    if len(os.fsencode(root / "run/mysql.sock")) > 103:
        raise ValueError("Performance MySQL socket path exceeds 103 bytes; use a shorter project path.")


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (bool, int)):
        return str(int(value))
    return "CONVERT(0x" + str(value).encode().hex() + " USING utf8mb4)"


def insert(stream, table, columns, rows):
    for start in range(0, len(rows), 500):
        stream.write(f"INSERT INTO erp.`{table}` ({columns}) VALUES\n")
        stream.write(",\n".join("(" + ",".join(map(literal, row)) + ")" for row in rows[start:start + 500]))
        stream.write(";\n")


def fixture(root, size, profile):
    """Only called immediately after initialize() created this new owned environment."""
    if (root / "performance-fixture.json").exists():
        raise ValueError("Refusing to reseed an existing performance fixture.")
    if int(dev.sql(root, "SELECT COUNT(*) FROM erp.user WHERE usernum LIKE 'perf-%'").strip()):
        raise ValueError("Performance users already exist; use a new environment.")
    sql_path = root / "run/performance-fixture.sql"
    teams = ["개발"] * 60 + ["디자인"] * 25 + ["경영지원"] * 15
    users = [f"perf-u{i:03}" for i in range(100)]
    notices, targets, approvals, lines, files, messages = [], [], [], [], [], []
    pairs = [(users[0], users[i]) for i in range(1, 100)] + [(users[1], users[2])]
    rooms = [(1000000 + i, a, b, (datetime(2026, 1, 1) + timedelta(seconds=i)).strftime("%Y-%m-%d %H:%M:%S")) for i, (a, b) in enumerate(pairs)]
    for i in range(size):
        number = 1000000 + i
        token = "rare" if i % 100 == 0 else "common" if i % 2 == 0 else "plain"
        notices.append((number, f"PERF {token} notice {i:06}", "고정 공지 본문 " * 32, i % 20 == 0, False, "admin", "2026-01-01 00:00:00"))
        targets.append((number, ["100", "200", "300", "999"][i % 4]))
        drafter = 0 if i < size // 2 else (i + SEED) % 97
        state = "승인" if i % 10 < 6 else "대기중" if i % 10 < 8 else "진행중" if i % 10 == 8 else "반려"
        approvals.append((number, "기안서", f"PERF {token} approval {i:06}", "고정 결재 본문 " * 64, state, users[drafter], f"PERF-{i:06}", "2026-01-01 00:00:00"))
        for order, target in enumerate(users[97:100], 1):
            status = "승인" if state == "승인" or state == "진행중" and order == 1 else "반려" if state == "반려" and order == 1 else "대기"
            lines.append((number, target, status, order, None if status == "대기" else "2026-01-01 00:01:00"))
        if i % 20 == 0:
            filename = f"perf-file-{number}.bin"
            payload = bytes(range(256)) * (4096 if i == 0 else 4)
            (root / "uploads" / filename).write_bytes(payload)
            files.append((number, filename, f"attachment-{i}.bin"))
        room_index = 0 if profile == "concentrated" and i % 5 else i % 100
        sender = pairs[room_index][(i // 100) % 2]
        messages.append((1000000 + i, 1000000 + room_index, sender, f"PERF history {i:06} " + "메시지" * 20, rooms[room_index][3], None if i % 5 == 0 else "2026-01-01 00:10:00"))
    with sql_path.open("w") as stream:
        stream.write("SET NAMES utf8mb4;\nSTART TRANSACTION;\n")
        password_hash = hashlib.md5(PASSWORD.encode()).hexdigest()
        insert(stream, "user", "usernum,userpw,name,team,level,level_num,firstlogin,authority,user_status", [(u, password_hash, f"Perf{i:03}", teams[i], "사원", 400, False, False, "재직") for i, u in enumerate(users)] + [("perf-admin", password_hash, "PerfAdmin", "경영지원", "관리자", 0, False, True, "재직")])
        insert(stream, "notice", "notice_no,notice_title,notice_content,is_important,is_main,usernum,noticedate", notices)
        insert(stream, "notice_team", "notice_no,notice_team", targets)
        insert(stream, "approval", "approval_no,kind,approval_title,approval_content,document_status,usernum,approval_code,writedate", approvals)
        insert(stream, "approval_line", "approval_no,approval_target,approval_status,approval_sort,approval_date", lines)
        insert(stream, "approval_file", "approval_no,apname,afname", files)
        insert(stream, "chat_room", "room_no,user_a,user_b,last_message_at", rooms)
        stream.write("UPDATE erp.chat_room SET created_at='2025-12-31 00:00:00' WHERE room_no>=1000000;\n")
        insert(stream, "chat_message", "message_no,room_no,sender_usernum,content,sent_at,read_at", messages)
        stream.write("COMMIT;\n")
    dev.sql(root, input_file=sql_path)
    sql_path.unlink()
    counts = {table: int(dev.sql(root, f"SELECT COUNT(*) FROM erp.`{table}`").strip()) for table in ["user", "notice", "notice_team", "approval", "approval_line", "approval_file", "chat_room", "chat_message", "user_number_sequence"]}
    assert counts["notice"] == size + 1 and counts["approval"] == size + 1
    assert counts["approval_line"] == size * 3 + 1 and counts["chat_message"] == size
    assert counts["approval_file"] == size // 20
    invalid = dev.sql(root, "SELECT COUNT(*) FROM erp.approval a JOIN (SELECT approval_no, CASE WHEN SUM(approval_status='반려')>0 THEN '반려' WHEN SUM(approval_status='승인')=COUNT(*) THEN '승인' WHEN SUM(approval_status='승인')>0 THEN '진행중' ELSE '대기중' END state FROM erp.approval_line GROUP BY approval_no) l ON l.approval_no=a.approval_no WHERE a.document_status<>l.state").strip()
    assert invalid == "0"
    manifest = {"seed": SEED, "size": size, "profile": profile, "counts": counts, "fixture_status_consistent": True, "attachment_count": len(files), "file_bytes": sum(p.stat().st_size for p in (root / "uploads").iterdir()), "base_fixture_rows_retained": True}
    write_json(root / "performance-fixture.json", manifest)
    return manifest


def distribution(values):
    values = sorted(values)
    if not values:
        return None
    return {"count": len(values), "mean_ms": sum(values) / len(values), "p50_ms": values[math.ceil(.5 * len(values)) - 1], "p95_ms": values[math.ceil(.95 * len(values)) - 1], "max_ms": values[-1]}


def summarize(output):
    groups = {}
    for name in ["http", "messages", "browser"]:
        path = output / f"{name}.jsonl"
        if not path.exists():
            continue
        for line in path.read_text().splitlines():
            row = json.loads(line)
            if row.get("warmup"):
                continue
            key = (name, row["repeat"], row.get("case", "delivery"), row.get("concurrency", 1))
            groups.setdefault(key, []).append(row)
    result = []
    for (kind, repeat, case, concurrency), rows in sorted(groups.items()):
        durations = [v["elapsed_ms"] for v in rows] if case not in {"integrity", "baseline"} else []
        good = [v["elapsed_ms"] for v in rows if v["ok"]] if case not in {"integrity", "baseline"} else []
        normal = sum(v["ok"] for v in rows)
        group = {"kind": kind, "repeat": repeat, "case": case, "concurrency": concurrency, "attempted": len(rows), "completed": sum(not r.get("timeout", False) for r in rows), "normal": normal, "failed": len(rows) - normal, "failure_rate": (len(rows) - normal) / len(rows), "all_attempts": distribution(durations), "success_latency": distribution(good)}
        if all("started_ns" in row and "completed_ns" in row for row in rows):
            elapsed = (max(r["completed_ns"] for r in rows) - min(r["started_ns"] for r in rows)) / 1e9
            group.update(wall_seconds=elapsed, closed_loop_attempts_per_second=len(rows) / elapsed)
        result.append(group)
    planned = {}
    if (output / "java-environment.json").exists():
        planned["http"] = json.loads((output / "java-environment.json").read_text())["planned_http_samples"]
    if (output / "browser-environment.json").exists():
        browser = json.loads((output / "browser-environment.json").read_text())
        planned["browser"] = browser["planned_samples"]
    write_json(output / "summary.json", {"quantile": "nearest rank ceil(p*N)", "planned_samples": planned, "attempted_samples": {kind: sum(g["attempted"] for g in result if g["kind"] == kind) for kind in ["http", "messages", "browser"]}, "groups": result, "note": "Closed-loop local synthetic samples; no maximum capacity, SLA or field INP claim. Planned minus attempted is not a successful request."})


def checked(command, log, env=None):
    with log.open("w") as stream:
        subprocess.run(command, cwd=PROJECT, env=env, stdout=stream, stderr=subprocess.STDOUT, check=True)


def self_check():
    assert literal("a'\\b") == "CONVERT(0x61275c62 USING utf8mb4)"
    assert distribution([1, 2, 3, 4])["p95_ms"] == 4
    assert distribution([]) is None
    validate_socket_path(PROJECT / ".local-dev/performance-12345678-100000")
    try:
        validate_socket_path(Path("/") / ("x" * 104))
    except ValueError:
        pass
    else:
        raise AssertionError("Long native socket paths must be rejected before initialization")
    try:
        dev.managed_root(PROJECT / ".local-dev/source/nested")
    except ValueError:
        pass
    else:
        raise AssertionError("Nested roots must be rejected")
    print("Performance helper self-check passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sizes", nargs="+", type=int, choices=[1000, 10000, 100000], default=[1000, 10000, 100000])
    parser.add_argument("--profile", choices=["uniform", "concentrated"], default="uniform")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--samples", type=int, default=50)
    parser.add_argument("--browser-samples", type=int, default=10)
    parser.add_argument("--messages", type=int, default=100)
    parser.add_argument("--mysql-bin", default="/opt/homebrew/opt/mysql@8.4/bin")
    parser.add_argument("--war", default=str(PROJECT / "target/erp-1.0.0-SNAPSHOT.war"))
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if args.self_check:
        self_check()
        return
    if args.repeats < 3 or min(args.samples, args.browser_samples, args.messages) < 1:
        parser.error("At least three repeats and positive sample counts are required.")
    os.umask(0o077)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    destination = PROJECT / "performance/results" / run_id
    destination.mkdir()
    for size in args.sizes:
        output = destination / str(size)
        output.mkdir()
        root = dev.managed_root(PROJECT / ".local-dev" / f"performance-{secrets.token_hex(4)}-{size}")
        validate_socket_path(root)
        if root.exists():
            raise ValueError("Performance runs require a new root.")
        memory, memory_absence = memory_size()
        code = set(subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard"], cwd=PROJECT, text=True).splitlines())
        code = sorted(name for name in code if name.startswith(("src/main/", "frontend/src/", "frontend/performance/")) or name in {"scripts/performance-run.py", "src/test/java/com/erp/NativePerformanceTest.java", "pom.xml", "frontend/package.json", "frontend/package-lock.json"})
        manifest = {"run_id": run_id, "root": str(root), "output": str(output), "condition": vars(args), "platform": platform.platform(), "cpu": platform.processor(), "cpu_count": os.cpu_count(), "physical_memory_bytes": memory, "memory_absence": memory_absence, "head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=PROJECT, text=True).strip(), "dirty_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD"], cwd=PROJECT)).hexdigest(), "source_sha256": {name: dev.digest(PROJECT / name) for name in code if (PROJECT / name).is_file()}, "mapper_sha256": {p.name: dev.digest(p) for p in (PROJECT / "src/main/resources/mapper").glob("*.xml")}, "bundle_sha256": {p.name: dev.digest(p) for p in (PROJECT / "src/main/resources/static/resources/app").rglob("*") if p.is_file()}, "war_sha256": dev.digest(Path(args.war)), "cache_condition": "Warm DB/OS; browser cold context and warm navigation separated. No OS cache eviction.", "hikari": {"maximum_pool_size": 10, "minimum_idle": 10, "source": "explicit performance-only app properties"}, "http": {"concurrency": [1, 5], "warmup_per_worker": 5, "timeout_seconds": 10, "think_time_ms": 0}, "messenger_deadline_seconds": 5}
        try:
            with dev.control_lock():
                state = dev.initialize(root, args.mysql_bin, args.war)
                assert root.name.startswith("performance-") and root != PROJECT / ".local-dev/source"
                manifest["fixture"] = fixture(root, size, args.profile)
                with (root / "run/app.properties").open("a") as stream:
                    stream.write("spring.datasource.hikari.maximum-pool-size=10\nspring.datasource.hikari.minimum-idle=10\n")
                dev.start(root)
                try:
                    manifest["effective_jvm_flags"] = subprocess.check_output(["jcmd", str(dev.app_pid(root)), "VM.flags"], text=True, timeout=10).splitlines()[1:]
                except (OSError, subprocess.SubprocessError):
                    manifest["effective_jvm_flags"] = None
                    manifest["jvm_flags_absence"] = "jcmd unavailable or attach denied"
            manifest["environment"] = {key: state[key] for key in ["root", "mysql_bin", "mysql_version", "db_port", "app_port", "war"]}
            manifest["url"] = f"http://127.0.0.1:{state['app_port']}/ERP"
            write_json(output / "manifest.json", manifest)
            env = dict(os.environ, ERP_PERF_ROOT=str(root), ERP_PERF_OUTPUT=str(output), ERP_PERF_REPEATS=str(args.repeats), ERP_PERF_SAMPLES=str(args.samples), ERP_PERF_MESSAGES=str(args.messages), ERP_PERF_BROWSER_SAMPLES=str(args.browser_samples))
            checked(["./mvnw", "--batch-mode", "-Dtest=NativePerformanceTest", "test"], output / "java.log", env)
            for repeat in range(1, args.repeats + 1):
                # Browser read receipts mutate only the fixture; reset outside the observation window.
                dev.sql(root, f"UPDATE erp.chat_message SET read_at=CASE WHEN MOD(message_no-1000000,5)=0 THEN NULL ELSE '2026-01-01 00:10:00' END WHERE message_no>=1000000 AND message_no<{1000000 + size}")
                checked(["node", "frontend/performance/run.mjs"], output / f"browser-{repeat}.log", dict(env, ERP_PERF_BROWSER_REPEAT=str(repeat)))
            manifest["status"] = "complete"
        except BaseException as failure:
            manifest["status"] = "failed"
            manifest["failure_type"] = type(failure).__name__
            raise
        finally:
            try:
                summary_failure = None
                try:
                    summarize(output)
                except Exception as failure:
                    summary_failure = failure
                    manifest.update(status="failed", summary_failure=type(failure).__name__, raw_samples_preserved=True)
                write_json(output / "manifest.json", manifest)
                if summary_failure:
                    raise RuntimeError("Summary failed; inspect the preserved raw samples.") from summary_failure
            finally:
                # Cleanup must run even when a raw line is incomplete or evidence writing fails.
                if (root / "environment.json").exists():
                    with dev.control_lock():
                        try:
                            dev.stop_app(root)
                        finally:
                            dev.stop_db(root)
                print(json.dumps({"size": size, "status": manifest.get("status"), "results": str(output), "environment_stopped": True}))


if __name__ == "__main__":
    main()
