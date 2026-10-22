#!/usr/bin/env python3
"""Exercise native init/restart/backup/restore with isolated development data."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
from http.cookiejar import CookieJar
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shlex
import shutil
import subprocess
import threading
import tarfile
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse, parse_qs
from urllib.request import build_opener, HTTPCookieProcessor, HTTPRedirectHandler, Request

spec = importlib.util.spec_from_file_location("dev_environment", Path(__file__).with_name("dev-environment.py"))
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, newurl):
        return None


class Session:
    def __init__(self, root, user="admin", password="1234"):
        self.base = f"http://127.0.0.1:{dev.load(root)['app_port']}/ERP"
        self.http = build_opener(HTTPCookieProcessor(CookieJar()), NoRedirect())
        _, _, body = self.request("GET", "/api/session")
        token = json.loads(body)["csrf"]
        status, _, body = self.request("POST", "/login/login.do", urlencode({
            "usernum": user, "userpw": password, token["parameterName"]: token["token"]}).encode(),
            {"Content-Type": "application/x-www-form-urlencoded"})
        assert status == 200 and body == b"OK", "Development fixture login failed"

    def request(self, method, path, body=None, headers=None):
        request = Request(self.base + path, data=body, method=method, headers=headers or {})
        try:
            response = self.http.open(request, timeout=10)
        except HTTPError as response_error:
            response = response_error
        with response:
            return response.status, response.headers, response.read()

    def token(self):
        status, _, body = self.request("GET", "/api/session")
        assert status == 200
        return json.loads(body)["csrf"]

    def write_json(self, method, path, body):
        token = self.token()
        return self.request(method, path, json.dumps(body).encode(), {
            "Content-Type": "application/json", token["headerName"]: token["token"]})


def employee(number, name):
    return {"usernum": number, "userpw": "1234", "name": name, "idnum1": "990101", "idnum2": "1",
            "phonenum": "010-1111-2222", "officenum": "02-111-2222", "email": "dev@example.com",
            "team": "개발", "level": "사원", "joindate": "2026-10-06", "authority": False, "userStatus": "재직"}


def verify_client_commands(root):
    assert dev.sql(root, "SELECT 1").strip() == "1"
    with tempfile.TemporaryDirectory(prefix="client-guard-", dir=dev.BASE) as directory:
        sentinel = Path(directory) / "should-not-exist"
        probe = Path(directory) / "probe.sql"
        probe.write_text("\\! touch " + shlex.quote(str(sentinel)) + "\n")
        try:
            dev.sql(root, input_file=probe)
        except subprocess.CalledProcessError as failure:
            assert ((b"Unknown command" in failure.stderr and b"\\!" in failure.stderr)
                    or b"ERROR 1064" in failure.stderr) and not sentinel.exists(), "Local mysql client command was not blocked"
        else:
            raise AssertionError("Local mysql client command was accepted")
    return True


def verify_environment_controls(root, document, attachment):
    state = dev.load(root)
    before = dev.snapshot(root)
    with tempfile.TemporaryDirectory(prefix="config-guard-", dir=dev.BASE) as directory:
        login_file = Path(directory) / "test.mylogin.cnf"
        overrides = {"MYSQL_TEST_LOGIN_FILE": str(login_file),
                     "SPRING_APPLICATION_JSON": json.dumps({"spring.datasource.url": "jdbc:mysql://127.0.0.1:1/guard", "server.port": 1}),
                     "spring.datasource.url": "jdbc:mysql://127.0.0.1:1/guard", "SPRING_SQL_INIT_MODE": "always",
                     "SERVER_PORT": "1", "DB_URL": "jdbc:mysql://127.0.0.1:1/guard",
                     "ERP_UPLOAD_DIR": str(Path(directory) / "outside-uploads"),
                     "JAVA_TOOL_OPTIONS": "-Dserver.port=1", "JDK_JAVA_OPTIONS": "-Dserver.port=1", "_JAVA_OPTIONS": "-Dserver.port=1"}
        dev.run([state["mysql_bin"] + "/mysql_config_editor", "set", "--login-path=client", "--user=wrong-environment",
                 "--host=127.0.0.1", "--socket=" + str(Path(directory) / "wrong.sock")],
                env={**os.environ, "MYSQL_TEST_LOGIN_FILE": str(login_file)}, capture_output=True)
        assert login_file.is_file()
        original = {key: os.environ.get(key) for key in overrides}
        try:
            os.environ.update(overrides)
            assert all(key not in dev.app_environment() for key in overrides if key != "MYSQL_TEST_LOGIN_FILE")
            assert dev.app_environment().get("ERP_AI_MODEL") == os.environ.get("ERP_AI_MODEL")
            dev.stop_app(root)
            dev.stop_db(root)
            dev.start(root)
            admin = Session(root)
            status, _, body = admin.request("GET", "/api/users")
            assert status == 200 and json.loads(body)["totalCount"] == int(dev.sql(root, "SELECT COUNT(*) FROM erp.user"))
            status, _, downloaded = Session(root, "2005004").request("GET", f"/approval/down.do?no={document}")
            assert status == 200 and downloaded == attachment
            dump = dev.run(dev.mysql_client_args(root, tool="mysqldump") +
                           ["--no-tablespaces", "--set-gtid-purged=OFF", "--no-data", "--databases", "erp"], capture_output=True)
            assert b"CREATE TABLE `user`" in dump.stdout
            assert dev.snapshot(root) == before and not (Path(directory) / "outside-uploads").exists()
        finally:
            for key, value in original.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value
    return True


def verify_backup_directory(root):
    base, pid = dev.BASE, dev.app_pid(root)
    with tempfile.TemporaryDirectory(prefix="backup-guard-", dir=base) as directory:
        owned, outside = Path(directory) / "owned", Path(directory) / "outside"
        owned.mkdir()
        outside.mkdir()
        (owned / "backups").symlink_to(outside, target_is_directory=True)
        try:
            dev.BASE = owned
            try:
                dev.backup(root)
            except ValueError as failure:
                assert "Backup directory" in str(failure)
            else:
                raise AssertionError("Backup symlink accepted")
        finally:
            dev.BASE = base
        assert not any(outside.iterdir()) and dev.app_pid(root) == pid
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(dev.BASE / "source"))
    args = parser.parse_args()
    source = dev.managed_root(args.root)
    state = dev.load(source)
    report = {"source_root": str(source), "mysql_version": state["mysql_version"], "war_sha256": state["war_sha256"]}
    for bad_root in [dev.PROJECT, source / "uploads/restore", dev.BASE / "backups"]:
        try:
            dev.managed_root(bad_root)
        except ValueError:
            pass
        else:
            raise AssertionError("Unsafe environment root accepted")
    competing = subprocess.run(["python3", str(dev.PROJECT / "scripts/dev-environment.py"), "status"], capture_output=True, timeout=5)
    assert competing.returncode != 0 and b"Another development environment command" in competing.stderr
    report["unsafe_roots_and_concurrent_control_rejected"] = True
    report["mysql_client_local_commands_rejected"] = verify_client_commands(source)
    report["backup_directory_symlink_rejected_before_app_stop"] = verify_backup_directory(source)
    admin = Session(source)
    status, _, body = admin.request("GET", "/api/users")
    assert status == 200 and json.loads(body)["totalCount"] == int(dev.sql(source, "SELECT COUNT(*) FROM erp.user"))
    report["authenticated_api_uses_source_db"] = True

    # Create one real attachment through the app, not by planting unrelated files.
    author = Session(source, "2005004")
    token = author.token()
    suffix = secrets.token_hex(4)
    title = "복구검증-" + suffix
    attachment = ("ERP native recovery " + suffix).encode() + bytes([0, 1, 127, 255])
    boundary = "ERP" + secrets.token_hex(12)
    form = {"kind": "기안서", "approval_title": title, "approval_content": "DB와 첨부 동일 시점 복구 검증",
            "approval_target": "2005003", token["parameterName"]: token["token"]}
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
             for key, value in form.items()]
    parts += [f'--{boundary}\r\nContent-Disposition: form-data; name="attach"; filename="recovery.bin"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode(), attachment, f"\r\n--{boundary}--\r\n".encode()]
    status, headers, _ = author.request("POST", "/approval/write.do", b"".join(parts), {"Content-Type": "multipart/form-data; boundary=" + boundary})
    assert status == 302
    document = int(parse_qs(urlparse(headers["Location"]).query)["approval_no"][0])
    path = f"/approval/down.do?no={document}"
    status, _, downloaded = author.request("GET", path)
    assert status == 200 and downloaded == attachment
    outsider = Session(source, "2005007")
    assert outsider.request("GET", path)[0] == 403
    report["source_attachment_download_matches"] = True
    report["unauthorized_attachment_status"] = 403
    report["attachment_sha256"] = hashlib.sha256(attachment).hexdigest()
    report["global_client_and_app_environment_overrides_rejected"] = verify_environment_controls(source, document, attachment)
    report["environment_override_attachment_download_matches"] = True
    admin = Session(source)

    # A deleted automatic number must remain above the restored sequence high-water mark.
    status, _, body = admin.write_json("POST", "/api/users", employee("", "복구번호" + suffix))
    assert status == 200
    deleted_number = json.loads(body)["usernum"]
    assert admin.write_json("DELETE", "/api/users/" + deleted_number, {})[0] == 200
    before_restart = dev.snapshot(source)
    dev.stop_app(source)
    dev.stop_db(source)
    dev.start(source)
    assert dev.snapshot(source) == before_restart
    assert Session(source, "2005004").request("GET", path)[2] == attachment
    report["restart_preserves_rows_and_bytes"] = True

    started = time.monotonic()
    backup = dev.backup(source)
    report["backup_seconds"] = round(time.monotonic() - started, 3)
    report["backup"] = str(backup)
    original = dev.snapshot(source)
    try:
        dev.restore(source, backup, state["mysql_bin"], state["war"])
    except ValueError as failure:
        assert "new empty" in str(failure)
    else:
        raise AssertionError("Restore overwrite accepted")
    report["existing_environment_restore_rejected"] = True
    tampered = dev.BASE / "backups" / ("tampered-" + suffix)
    shutil.copytree(backup, tampered)
    with (tampered / "database.sql").open("ab") as stream:
        stream.write(b"\n-- checksum tamper probe\n")
    guard = dev.BASE / ("guard-" + suffix)
    try:
        dev.restore(guard, tampered, state["mysql_bin"], state["war"])
    except ValueError as failure:
        assert "checksum mismatch" in str(failure) and not guard.exists()
    else:
        raise AssertionError("Tampered backup accepted")
    shutil.copyfile(backup / "database.sql", tampered / "database.sql")
    manifest = json.loads((tampered / "manifest.json").read_text())
    with tarfile.open(tampered / "uploads.tar.gz", "w:gz") as tar:
        entry = tarfile.TarInfo("../outside.txt")
        entry.size = 1
        tar.addfile(entry, io.BytesIO(b"x"))
    manifest["snapshot"]["uploads"] = {"../outside.txt": {"size": 1, "sha256": hashlib.sha256(b"x").hexdigest()}}
    manifest["payloads"]["uploads.tar.gz"] = dev.digest(tampered / "uploads.tar.gz")
    (tampered / "manifest.json").write_text(json.dumps(manifest))
    try:
        dev.restore(guard, tampered, state["mysql_bin"], state["war"])
    except ValueError as failure:
        assert "Unsafe attachment" in str(failure) and not guard.exists()
    else:
        raise AssertionError("Archive traversal accepted")
    shutil.rmtree(tampered)
    report["checksum_and_archive_traversal_rejected_before_init"] = True
    guard.mkdir()
    dev.private_file(guard / "environment.json", json.dumps({"root": str(guard)}))
    (guard / "uploads").symlink_to(source / "uploads", target_is_directory=True)
    try:
        dev.load(guard)
    except ValueError as failure:
        assert "symlinks" in str(failure)
    else:
        raise AssertionError("Upload root symlink accepted")
    (guard / "uploads").unlink()
    (guard / "environment.json").unlink()
    guard.rmdir()
    report["upload_root_symlink_rejected"] = True
    restored_root = dev.BASE / ("restore-" + suffix)
    started = time.monotonic()
    dev.restore(restored_root, backup, state["mysql_bin"], state["war"])
    restored_state = dev.load(restored_root)
    assert restored_state["db_port"] != state["db_port"] and restored_state["app_port"] != state["app_port"]
    assert dev.snapshot(restored_root) == original
    assert Session(restored_root, "2005004").request("GET", path)[2] == attachment
    report["restore_seconds_through_download"] = round(time.monotonic() - started, 3)
    report["restore_root"] = str(restored_root)
    report["restored_snapshot_matches"] = True
    report["restored_attachment_download_matches"] = True

    restored_admins = [Session(restored_root), Session(restored_root)]
    barrier = threading.Barrier(2)
    def register(index):
        barrier.wait(timeout=10)
        status, _, body = restored_admins[index].write_json("POST", "/api/users", employee("", "복원동시" + str(index)))
        assert status == 200
        return json.loads(body)["usernum"]
    with ThreadPoolExecutor(max_workers=2) as pool:
        numbers = list(pool.map(register, [0, 1]))
    assert len(set(numbers)) == 2 and all(number > deleted_number for number in numbers)
    assert dev.snapshot(source) == original, "Restore verification modified the source environment"
    report["restored_distinct_numbers_after_deleted_number"] = True
    report["restored_number_count"] = len(set(numbers))
    report["source_unchanged_by_restore_checks"] = True
    report["source_url"] = f"http://127.0.0.1:{state['app_port']}/ERP"
    report["restore_url"] = f"http://127.0.0.1:{restored_state['app_port']}/ERP"
    destination = dev.PROJECT / "docs/refactoring/review-evidence/native-recovery.json"
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    os.umask(0o077)
    with dev.control_lock():
        main()
