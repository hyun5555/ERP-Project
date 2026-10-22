#!/usr/bin/env python3
"""Project-owned native MySQL/WAR environment; no Docker or system services."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import secrets
import shutil
import signal
import socket
import subprocess
import tarfile
import time
from datetime import datetime, timezone
from urllib.request import urlopen

PROJECT = Path(__file__).resolve().parents[1]
BASE = PROJECT / ".local-dev"


def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, timeout=120, **kwargs)


def private_file(path, text):
    path.write_text(text)
    path.chmod(0o600)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def managed_root(value):
    root = Path(value).resolve()
    if BASE.is_symlink() or Path(value).is_symlink() or root.parent != BASE or root.name == "backups":
        raise ValueError("Environment must be a direct, non-symlink child of .local-dev, excluding backups.")
    return root


@contextmanager
def control_lock():
    if BASE.is_symlink():
        raise ValueError(".local-dev must not be a symlink.")
    BASE.mkdir(mode=0o700, exist_ok=True)
    path = BASE / ".control.lock"
    if path.is_symlink():
        raise ValueError("Control lock must not be a symlink.")
    with path.open("a") as stream:
        path.chmod(0o600)
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("Another development environment command is running.") from None
        yield


def load(root):
    state = json.loads((root / "environment.json").read_text())
    if state["root"] != str(root):
        raise ValueError("Environment identity does not match this directory.")
    for name in ["mysql", "mysql/data", "uploads", "run", "logs", "environment.json"]:
        path = root / name
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError("Environment paths must stay inside their owned root without symlinks.")
    properties = root / "run/app.properties"
    if properties.exists():
        values = dict(line.split("=", 1) for line in properties.read_text().splitlines() if "=" in line)
        expected = {"server.address": "127.0.0.1", "server.port": str(state["app_port"]),
                    "erp.upload-dir": str(root / "uploads"), "spring.sql.init.mode": "never",
                    "spring.datasource.url": f"jdbc:mysql://127.0.0.1:{state['db_port']}/erp?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true"}
        if any(values.get(key) != value for key, value in expected.items()):
            raise ValueError("Generated app configuration no longer matches its isolated environment.")
    return state


def mysql_client_args(root, tool="mysql", client="admin"):
    args = [load(root)["mysql_bin"] + "/" + tool, f"--defaults-file={root}/run/{client}.cnf",
            "--no-login-paths", "--protocol=SOCKET", f"--socket={root}/run/mysql.sock"]
    return args + (["--binary-mode"] if tool == "mysql" else [])


def sql(root, query=None, input_file=None, client="admin"):
    args = mysql_client_args(root, client=client) + ["--batch", "--skip-column-names"]
    if query is not None:
        # Fresh native metadata is required: cached I_S AUTO_INCREMENT can lag writes.
        return run(args, input=("SET SESSION information_schema_stats_expiry=0;\n" + query + ";\n").encode(), capture_output=True).stdout.decode()
    with input_file.open("rb") as stream:
        run(args, stdin=stream, capture_output=True)


def spawn(args, logfile, env=None):
    with logfile.open("ab") as output:
        return subprocess.Popen([str(a) for a in args], stdout=output, stderr=output,
                                start_new_session=True, env=env)


def app_environment():
    result = {}
    for key, value in os.environ.items():
        normalized = key.upper().replace(".", "_").replace("-", "_")
        if (normalized.startswith(("SPRING_", "SERVER_", "DB_", "ERP_UPLOAD"))
                or normalized in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}):
            continue
        result[key] = value
    return result


def start_db(root, bootstrap=False):
    state = load(root)
    args = mysql_client_args(root, client="bootstrap" if bootstrap else "admin") + ["--execute", "SELECT 1"]
    if subprocess.run(args, capture_output=True).returncode == 0:
        return
    command = [state["mysql_bin"] + "/mysqld", "--no-defaults", f"--datadir={root}/mysql/data",
               "--bind-address=127.0.0.1", f"--port={state['db_port']}", f"--socket={root}/run/mysql.sock",
               f"--pid-file={root}/run/mysql.pid", f"--log-error={root}/logs/mysql.log", "--mysqlx=OFF"]
    if bootstrap:
        command.append("--skip-networking")
    process = spawn(command, root / "logs/mysql-console.log")
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"MySQL exited; inspect {root}/logs/mysql.log")
        if subprocess.run(args, capture_output=True).returncode == 0:
            return
        time.sleep(0.2)
    raise RuntimeError(f"MySQL did not become ready; inspect {root}/logs/mysql.log")


def stop_db(root):
    if (root / "run/mysql.sock").exists():
        run(mysql_client_args(root, tool="mysqladmin") + ["shutdown"], capture_output=True)


def app_pid(root):
    path = root / "run/app.pid"
    if not path.exists():
        return None
    pid = int(path.read_text())
    command = subprocess.run(["ps", "-p", str(pid), "-o", "command="], capture_output=True, text=True)
    if command.returncode != 0 or f"file:{root}/run/app.properties" not in command.stdout:
        return None
    return pid


def stop_app(root):
    pid = app_pid(root)
    if pid is None:
        return
    os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + 20
    while app_pid(root) is not None and time.monotonic() < deadline:
        time.sleep(0.2)
    if app_pid(root) is not None:
        raise RuntimeError("App did not stop cleanly; backup cancelled.")


def initialize(root, mysql_bin, war, seed=True):
    if root.exists() and any(root.iterdir()):
        raise ValueError("Initialization/restore requires a new empty environment directory.")
    mysql_bin = Path(mysql_bin).resolve()
    war = Path(war).resolve()
    if not war.is_file() or not (mysql_bin / "mysqld").is_file():
        raise ValueError("Build the executable WAR and supply a native MySQL 8.4 bin directory.")
    version = run([mysql_bin / "mysqld", "--version"], capture_output=True, text=True).stdout.strip()
    if "Ver 8.4." not in version:
        raise ValueError("This development environment requires MySQL 8.4.")
    for name in ["mysql/data", "uploads", "run", "logs"]:
        (root / name).mkdir(parents=True, exist_ok=True)
    root.chmod(0o700)
    db_port, app_port = free_port(), free_port()
    while app_port == db_port:
        app_port = free_port()
    state = {"root": str(root), "mysql_bin": str(mysql_bin), "mysql_version": version,
             "schema_files": {name: digest(PROJECT / name) for name in ["document/ERP.sql", "src/main/resources/schema.sql"]},
             "war": str(war), "war_sha256": digest(war), "db_port": db_port, "app_port": app_port}
    private_file(root / "environment.json", json.dumps(state, indent=2) + "\n")
    root_password, app_password = secrets.token_hex(24), secrets.token_hex(24)
    client_options = f"[client]\nuser=root\nprotocol=socket\nsocket={root}/run/mysql.sock\n"
    private_file(root / "run/bootstrap.cnf", client_options)
    private_file(root / "run/admin.cnf", client_options + f"password={root_password}\n")
    private_file(root / "run/app.properties", f"""server.address=127.0.0.1
server.port={app_port}
spring.datasource.url=jdbc:mysql://127.0.0.1:{db_port}/erp?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=erp_dev
spring.datasource.password={app_password}
spring.sql.init.mode=never
erp.upload-dir={root}/uploads
""")
    run([mysql_bin / "mysqld", "--no-defaults", "--initialize-insecure", f"--datadir={root}/mysql/data"],
        capture_output=True)
    start_db(root, bootstrap=True)
    bootstrap = mysql_client_args(root, client="bootstrap")
    statements = f"""ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';
CREATE DATABASE erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'erp_dev'@'127.0.0.1' IDENTIFIED BY '{app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON erp.* TO 'erp_dev'@'127.0.0.1';
"""
    run(bootstrap, input=statements.encode(), capture_output=True)
    (root / "run/bootstrap.cnf").unlink()
    if seed:
        sql(root, input_file=PROJECT / "document/ERP.sql")
        run(mysql_client_args(root) + ["erp"],
            input=(PROJECT / "src/main/resources/schema.sql").read_bytes(), capture_output=True)
    stop_db(root)
    start_db(root)
    return state


def start(root):
    state = load(root)
    if digest(Path(state["war"])) != state["war_sha256"]:
        raise ValueError("WAR changed since initialization; use the matching artifact or a new environment.")
    start_db(root)
    if app_pid(root) is None:
        process = spawn(["java", "-jar", state["war"],
                         f"--spring.config.additional-location=file:{root}/run/app.properties"], root / "logs/app.log",
                        env=app_environment())
        private_file(root / "run/app.pid", str(process.pid))
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            with urlopen(f"http://127.0.0.1:{state['app_port']}/ERP/api/session", timeout=1) as response:
                if response.status == 200:
                    return
        except (OSError, ValueError):
            pass
        if app_pid(root) is None:
            raise RuntimeError(f"App exited; inspect {root}/logs/app.log")
        time.sleep(0.2)
    raise RuntimeError(f"App did not become ready; inspect {root}/logs/app.log")


def snapshot(root):
    tables = sql(root, "SHOW TABLES FROM erp").splitlines()
    result = {}
    for table in sorted(tables):
        quoted = "`" + table.replace("`", "``") + "`"
        columns = [line.split("\t")[0] for line in sql(root, f"SHOW COLUMNS FROM erp.{quoted}").splitlines()]
        order = ",".join("`" + name.replace("`", "``") + "`" for name in columns)
        rows = sql(root, f"SELECT * FROM erp.{quoted} ORDER BY {order}")
        table_filter = f"TABLE_SCHEMA='erp' AND TABLE_NAME=0x{table.encode().hex()}"
        table_options = sql(root, f"SELECT ENGINE,TABLE_COLLATION,AUTO_INCREMENT,CREATE_OPTIONS FROM information_schema.TABLES WHERE {table_filter}")
        if not table_options.startswith("InnoDB\tutf8mb4_"):
            raise ValueError("Backup verification requires InnoDB and utf8mb4 tables.")
        # SHOW CREATE text can add redundant CHARACTER SET clauses after mysqldump.
        # Compare native metadata, including indexes, FK/check definitions and auto increment.
        metadata_queries = [
            f"SELECT ORDINAL_POSITION,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE {table_filter} ORDER BY ORDINAL_POSITION",
            f"SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION,SUB_PART,INDEX_TYPE,EXPRESSION,IS_VISIBLE FROM information_schema.STATISTICS WHERE {table_filter} ORDER BY INDEX_NAME,SEQ_IN_INDEX",
            f"SELECT CONSTRAINT_NAME,CONSTRAINT_TYPE,ENFORCED FROM information_schema.TABLE_CONSTRAINTS WHERE {table_filter} ORDER BY CONSTRAINT_NAME",
            f"SELECT CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,POSITION_IN_UNIQUE_CONSTRAINT,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE {table_filter} ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION",
            f"SELECT CONSTRAINT_NAME,UNIQUE_CONSTRAINT_NAME,MATCH_OPTION,UPDATE_RULE,DELETE_RULE,REFERENCED_TABLE_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='erp' AND TABLE_NAME=0x{table.encode().hex()} ORDER BY CONSTRAINT_NAME",
            f"SELECT c.CONSTRAINT_NAME,c.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA='erp' AND t.TABLE_NAME=0x{table.encode().hex()} ORDER BY c.CONSTRAINT_NAME"]
        metadata = [table_options] + [sql(root, query) for query in metadata_queries]
        result[table] = {"count": int(sql(root, f"SELECT COUNT(*) FROM erp.{quoted}")),
                         "schema_sha256": hashlib.sha256(json.dumps(metadata).encode()).hexdigest(),
                         "rows_sha256": hashlib.sha256(rows.encode()).hexdigest()}
    files = {}
    for path in sorted((root / "uploads").rglob("*")):
        if path.is_symlink():
            raise ValueError("Upload symlinks are not supported.")
        if path.is_file():
            files[path.relative_to(root / "uploads").as_posix()] = {"size": path.stat().st_size, "sha256": digest(path)}
    references = sql(root, "SELECT apname FROM erp.approval_file UNION SELECT pname FROM erp.notice WHERE pname IS NOT NULL AND pname <> ''").splitlines()
    if any(name not in files for name in references):
        raise ValueError("A DB attachment reference has no matching upload file.")
    # Check restored FK data explicitly; importing a dump temporarily disables FK enforcement.
    orphan_count = 0
    for child, key, parent, parent_key in [("approval", "usernum", "user", "usernum"),
            ("approval_line", "approval_target", "user", "usernum"), ("approval_line", "approval_no", "approval", "approval_no"),
            ("approval_file", "approval_no", "approval", "approval_no"), ("notice", "usernum", "user", "usernum"),
            ("notice_team", "notice_no", "notice", "notice_no"), ("chat_room", "user_a", "user", "usernum"),
            ("chat_room", "user_b", "user", "usernum"), ("chat_message", "room_no", "chat_room", "room_no"),
            ("chat_message", "sender_usernum", "user", "usernum")]:
        orphan_count += int(sql(root, f"SELECT COUNT(*) FROM erp.`{child}` c LEFT JOIN erp.`{parent}` p ON c.`{key}`=p.`{parent_key}` WHERE c.`{key}` IS NOT NULL AND p.`{parent_key}` IS NULL"))
    if orphan_count:
        raise ValueError("Foreign key orphan rows found.")
    return {"tables": result, "uploads": files, "attachment_references": sorted(references), "fk_orphans": orphan_count}


def backup(root):
    state = load(root)
    parent = BASE / "backups"
    if parent.is_symlink() or not parent.resolve().is_relative_to(BASE):
        raise ValueError("Backup directory must stay inside .local-dev without symlinks.")
    was_running = app_pid(root) is not None
    stop_app(root)
    destination = parent / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3))
    destination.mkdir(parents=True)
    started_at = datetime.now(timezone.utc).isoformat()
    try:
        content = snapshot(root)
        dump = destination / "database.sql"
        with dump.open("wb") as output:
            run(mysql_client_args(root, tool="mysqldump") +
                ["--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF", "--databases", "erp"],
                stdout=output, stderr=subprocess.PIPE)
        archive = destination / "uploads.tar.gz"
        with tarfile.open(archive, "w:gz") as tar:
            for name in content["uploads"]:
                tar.add(root / "uploads" / name, arcname=name, recursive=False)
        manifest = {"status": "exported", "started_at": started_at,
                    "created_at": datetime.now(timezone.utc).isoformat(), "source_root": str(root),
                    "schema_files": state.get("schema_files", {}),
                    "mysql_version": state["mysql_version"], "war_sha256": state["war_sha256"], "snapshot": content,
                    "payloads": {path.name: digest(path) for path in [dump, archive]}}
        private_file(destination / "manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    finally:
        if was_running:
            start(root)
    manifest.update(status="complete", completed_at=datetime.now(timezone.utc).isoformat(),
                    app_restarted=was_running)
    private_file(destination / "manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    return destination


def archive_members(tar, expected):
    members = tar.getmembers()
    if sorted(m.name for m in members) != sorted(expected):
        raise ValueError("Archive members do not match the manifest.")
    for member in members:
        name = PurePosixPath(member.name)
        if (not member.isfile() or name.is_absolute() or ".." in name.parts
                or member.size != expected[member.name]["size"]):
            raise ValueError("Unsafe attachment archive member.")
    return members


def restore(root, source, mysql_bin, war):
    source = Path(source).resolve()
    manifest = json.loads((source / "manifest.json").read_text())
    if manifest.get("status") != "complete":
        raise ValueError("Only completed backups generated by this tool may be restored.")
    if digest(Path(war).resolve()) != manifest["war_sha256"]:
        raise ValueError("Restore requires the same WAR SHA-256 as the backup.")
    for name in ["database.sql", "uploads.tar.gz"]:
        if digest(source / name) != manifest["payloads"][name]:
            raise ValueError(f"Backup checksum mismatch: {name}")
    expected = manifest["snapshot"]["uploads"]
    with tarfile.open(source / "uploads.tar.gz") as tar:
        archive_members(tar, expected)
    initialize(root, mysql_bin, war, seed=False)
    restore_password = secrets.token_hex(24)
    sql(root, f"CREATE USER 'erp_restore'@'localhost' IDENTIFIED BY '{restore_password}'; GRANT ALL ON erp.* TO 'erp_restore'@'localhost'")
    restore_config = root / "run/restore.cnf"
    private_file(restore_config, f"[client]\nuser=erp_restore\npassword={restore_password}\nprotocol=socket\nsocket={root}/run/mysql.sock\n")
    try:
        sql(root, input_file=source / "database.sql", client="restore")
    finally:
        sql(root, "DROP USER 'erp_restore'@'localhost'")
        restore_config.unlink()
    with tarfile.open(source / "uploads.tar.gz") as tar:
        for member in archive_members(tar, expected):
            target = root / "uploads" / member.name
            target.parent.mkdir(parents=True, exist_ok=True)
            with tar.extractfile(member) as stream, target.open("wb") as output:
                shutil.copyfileobj(stream, output)
    if snapshot(root) != manifest["snapshot"]:
        raise ValueError("Restored rows, FK references or attachment bytes differ from the backup.")
    start(root)
    return {"restored_from": str(source), "snapshot_matches": True}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(BASE / "source"))
    parser.add_argument("--mysql-bin", default=os.environ.get("ERP_MYSQL_BIN", "/opt/homebrew/opt/mysql@8.4/bin"))
    parser.add_argument("--war", default=str(PROJECT / "target/erp-1.0.0-SNAPSHOT.war"))
    parser.add_argument("command", choices=["init", "start", "stop", "status", "check", "backup", "restore"])
    parser.add_argument("--backup", help="Backup directory required by restore")
    args = parser.parse_args()
    root = managed_root(args.root)
    if args.command == "init":
        result = initialize(root, args.mysql_bin, args.war)
    elif args.command == "start":
        start(root)
        result = {"started": True}
    elif args.command == "stop":
        stop_app(root)
        stop_db(root)
        result = {"stopped": True}
    elif args.command == "status":
        state = load(root)
        try:
            db_running = sql(root, "SELECT 1").strip() == "1"
        except subprocess.SubprocessError:
            db_running = False
        result = {"app_running": app_pid(root) is not None, "db_running": db_running,
                  "url": f"http://127.0.0.1:{state['app_port']}/ERP", "db_port": state["db_port"], "uploads": str(root / "uploads")}
    elif args.command == "check":
        state = load(root)
        with urlopen(f"http://127.0.0.1:{state['app_port']}/ERP/api/session", timeout=5) as response:
            session = json.load(response)
        if session["authenticated"] is not False:
            raise ValueError("Anonymous session check failed.")
        result = {"anonymous_session_ok": session["authenticated"] is False, "snapshot": snapshot(root)}
    elif args.command == "backup":
        result = {"backup": str(backup(root))}
    else:
        if not args.backup:
            parser.error("restore requires --backup")
        result = restore(root, args.backup, args.mysql_bin, args.war)
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    try:
        with control_lock():
            main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as failure:
        # Do not print SQL input, credential files or subprocess arguments containing secrets.
        raise SystemExit(f"Development environment failed: {failure}")
