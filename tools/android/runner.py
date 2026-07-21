#!/usr/bin/env python3
"""Deterministic Android debug scenario runner for Legado."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from typing import Any, Sequence
import xml.etree.ElementTree as ET


EXIT_ASSERTION = 1
EXIT_CRASH = 2
EXIT_BUILD = 3
EXIT_ENVIRONMENT = 4
EXIT_SCENARIO = 5

PACKAGE = "io.legato.kazusa.debug"
DEBUG_ACTIVITY = "io.legado.app.debug.DebugScenarioActivity"
MAIN_ACTIVITY = "io.legado.app.ui.main.MainActivity"

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent
SCENARIO_ROOT = SCRIPT_DIR / "scenarios"
RUN_ROOT = PROJECT_ROOT / ".debug-runs"
FIXTURE_ROOT = PROJECT_ROOT / "app" / "src" / "debug" / "assets" / "debug-fixtures"


class ScenarioError(ValueError):
    pass


class CommandError(RuntimeError):
    def __init__(self, command: Sequence[str], result: subprocess.CompletedProcess[str]):
        self.command = list(command)
        self.result = result
        super().__init__(f"command failed ({result.returncode}): {' '.join(command)}")


def load_scenario(name: str) -> dict[str, Any]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9/_-]*", name):
        raise ScenarioError(f"invalid scenario name: {name}")
    path = (SCENARIO_ROOT / f"{name}.json").resolve()
    if SCENARIO_ROOT.resolve() not in path.parents or not path.is_file():
        raise ScenarioError(f"scenario not found: {name}")
    try:
        scenario = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ScenarioError(f"cannot read scenario {name}: {error}") from error
    validate_scenario(scenario, name)
    return scenario


def validate_scenario(scenario: Any, requested_name: str | None = None) -> None:
    if not isinstance(scenario, dict):
        raise ScenarioError("scenario root must be an object")
    if scenario.get("schemaVersion") != 1:
        raise ScenarioError("schemaVersion must be 1")
    name = scenario.get("name")
    if not isinstance(name, str) or not name:
        raise ScenarioError("name must be a non-empty string")
    if requested_name is not None and name != requested_name:
        raise ScenarioError(f"scenario name mismatch: expected {requested_name}, got {name}")
    fixture = scenario.get("fixture")
    if not isinstance(fixture, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]*", fixture):
        raise ScenarioError("fixture must be a lowercase ID")
    marker = scenario.get("readyMarker")
    if not isinstance(marker, str) or not marker.strip():
        raise ScenarioError("readyMarker must be a non-empty string")
    timeout = scenario.get("readyTimeoutSeconds", 30)
    if not isinstance(timeout, int) or timeout < 1 or timeout > 300:
        raise ScenarioError("readyTimeoutSeconds must be between 1 and 300")
    entry = scenario.get("entry")
    if not isinstance(entry, dict) or entry.get("type") != "reader":
        raise ScenarioError("entry.type must be reader")

    actions = scenario.get("actions")
    if not isinstance(actions, list) or not actions:
        raise ScenarioError("actions must be a non-empty array")
    for index, action in enumerate(actions):
        if not isinstance(action, dict) or action.get("type") != "turn_page":
            raise ScenarioError(f"actions[{index}] has an unsupported type")
        if action["type"] == "turn_page":
            if action.get("direction") not in {"next", "previous"}:
                raise ScenarioError(f"actions[{index}].direction must be next or previous")
            count = action.get("count", 1)
            if not isinstance(count, int) or count < 1 or count > 100:
                raise ScenarioError(f"actions[{index}].count must be between 1 and 100")

    assertions = scenario.get("assertions")
    supported = {
        "reader_ready",
        "reader_activity_visible",
        "reader_content_changed",
        "no_fatal_exception",
    }
    if not isinstance(assertions, list) or not assertions:
        raise ScenarioError("assertions must be a non-empty array")
    unknown = set(assertions) - supported
    if unknown:
        raise ScenarioError(f"unsupported assertions: {', '.join(sorted(unknown))}")


def validate_fixture(fixture_id: str) -> dict[str, Any]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", fixture_id):
        raise ScenarioError("fixture must be a lowercase ID")
    path = FIXTURE_ROOT / f"{fixture_id}.json"
    if not path.is_file():
        raise ScenarioError(f"fixture not found: {fixture_id}")
    try:
        fixture = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ScenarioError(f"cannot read fixture {fixture_id}: {error}") from error
    validate_fixture_data(fixture, fixture_id)
    return fixture


def validate_fixture_data(fixture: Any, fixture_id: str) -> None:
    if not isinstance(fixture, dict):
        raise ScenarioError(f"fixture root must be an object: {fixture_id}")
    if fixture.get("schemaVersion") != 1 or fixture.get("id") != fixture_id:
        raise ScenarioError(f"invalid fixture metadata: {fixture_id}")
    if fixture.get("resetPolicy") != "replace":
        raise ScenarioError(f"unsupported fixture resetPolicy: {fixture_id}")
    book = fixture.get("book")
    if not isinstance(book, dict):
        raise ScenarioError(f"fixture book must be an object: {fixture_id}")
    required_strings = ("bookUrl", "name", "author")
    if any(not isinstance(book.get(key), str) or not book[key] for key in required_strings):
        raise ScenarioError(f"fixture book metadata is incomplete: {fixture_id}")
    if book.get("pageMode") != "simulation":
        raise ScenarioError(f"unsupported fixture pageMode: {fixture_id}")
    chapters = fixture.get("chapters")
    if not isinstance(chapters, list) or not chapters:
        raise ScenarioError(f"fixture must contain chapters: {fixture_id}")
    start_chapter = book.get("startChapter")
    start_position = book.get("startPosition")
    if not isinstance(start_chapter, int) or start_chapter not in range(len(chapters)):
        raise ScenarioError(f"fixture startChapter is invalid: {fixture_id}")
    if not isinstance(start_position, int) or start_position < 0:
        raise ScenarioError(f"fixture startPosition is invalid: {fixture_id}")
    for index, chapter in enumerate(chapters):
        if not isinstance(chapter, dict):
            raise ScenarioError(f"fixture chapters[{index}] must be an object")
        for key in ("title", "readyMarker", "paragraph"):
            if not isinstance(chapter.get(key), str) or not chapter[key]:
                raise ScenarioError(f"fixture chapters[{index}].{key} is required")
        repeat = chapter.get("repeat")
        if not isinstance(repeat, int) or repeat < 1 or repeat > 1000:
            raise ScenarioError(f"fixture chapters[{index}].repeat must be between 1 and 1000")


def run_command(
    command: Sequence[str],
    *,
    timeout: int = 120,
    check: bool = True,
    cwd: Path = PROJECT_ROOT,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        list(command),
        cwd=cwd,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
    )
    if check and result.returncode != 0:
        raise CommandError(command, result)
    return result


def adb(device: str, *arguments: str, timeout: int = 60, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run_command(["adb", "-s", device, *arguments], timeout=timeout, check=check)


def select_device(requested: str | None) -> str:
    if shutil.which("adb") is None:
        raise RuntimeError("adb is not available on PATH")
    result = run_command(["adb", "devices"], timeout=15)
    devices = []
    for line in result.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            devices.append(fields[0])
    if requested:
        if requested not in devices:
            raise RuntimeError(f"device is not ready: {requested}")
        return requested
    if len(devices) == 1:
        return devices[0]
    if not devices:
        raise RuntimeError("no ready Android device found")
    raise RuntimeError("multiple devices found; pass --device")


def create_session(scenario_name: str, fixture: str, device: str | None) -> tuple[str, Path, dict[str, Any]]:
    now = dt.datetime.now().astimezone()
    session_id = now.strftime("%Y%m%d-%H%M%S-%f")[:-3]
    run_dir = RUN_ROOT / session_id
    run_dir.mkdir(parents=True, exist_ok=False)
    metadata = {
        "schemaVersion": 1,
        "sessionId": session_id,
        "scenario": scenario_name,
        "fixture": fixture,
        "device": device,
        "startedAt": now.isoformat(timespec="milliseconds"),
        "package": PACKAGE,
    }
    write_json(run_dir / "metadata.json", metadata)
    return session_id, run_dir, metadata


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def find_debug_apk() -> Path:
    output_root = PROJECT_ROOT / "app" / "build" / "outputs" / "apk"
    candidates = [
        path
        for path in output_root.rglob("*.apk")
        if "androidTest" not in path.parts and "debug" in path.parts
    ]
    if not candidates:
        raise RuntimeError("debug APK was not produced")
    universal = [path for path in candidates if "universal" in path.name]
    return max(universal or candidates, key=lambda path: path.stat().st_mtime)


def build_and_install(device: str, run_dir: Path) -> tuple[dict[str, Any], Path]:
    started = time.monotonic()
    command = ["./gradlew", ":app:assembleAppDebug", "-PenableAbiSplits=false"]
    result = run_command(command, timeout=900, check=False)
    (run_dir / "build.log").write_text(result.stdout, encoding="utf-8")
    build = {
        "status": "passed" if result.returncode == 0 else "failed",
        "durationMs": round((time.monotonic() - started) * 1000),
        "artifact": "build.log",
    }
    if result.returncode != 0:
        raise CommandError(command, result)
    apk = find_debug_apk()
    install_mode = install_apk(device, apk, run_dir)
    build["apk"] = str(apk.relative_to(PROJECT_ROOT))
    build["installMode"] = install_mode
    return build, apk


def install_apk(device: str, apk: Path, run_dir: Path) -> str:
    attempts = [
        ("no_streaming", ["adb", "-s", device, "install", "--no-streaming", "-r", "-t", str(apk)], 120),
        ("streaming_retry", ["adb", "-s", device, "install", "-r", "-t", str(apk)], 60),
    ]
    log_parts = []
    last_error: Exception | None = None
    for mode, command, timeout in attempts:
        try:
            result = run_command(command, timeout=timeout, check=False)
            log_parts.append(f"[{mode}] exit={result.returncode}\n{result.stdout}")
            if result.returncode == 0 and "Success" in result.stdout:
                (run_dir / "install.log").write_text("\n".join(log_parts), encoding="utf-8")
                return mode
            last_error = CommandError(command, result)
        except subprocess.TimeoutExpired as error:
            log_parts.append(f"[{mode}] timeout={timeout}s\n")
            last_error = error
        cleanup_device(device)
    (run_dir / "install.log").write_text("\n".join(log_parts), encoding="utf-8")
    raise RuntimeError(f"APK install failed after {len(attempts)} attempts: {last_error}")


def launch_fixture(device: str, fixture: str, session_id: str) -> None:
    adb(device, "shell", "am", "force-stop", PACKAGE)
    adb(device, "logcat", "-c")
    component = f"{PACKAGE}/{DEBUG_ACTIVITY}"
    result = adb(
        device,
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        component,
        "--es",
        "fixture",
        fixture,
        "--es",
        "session",
        session_id,
        timeout=60,
        check=False,
    )
    if result.returncode != 0 or "Error:" in result.stdout:
        raise CommandError(["adb", "shell", "am", "start", component], result)


def ui_dump(device: str) -> str:
    return adb(device, "exec-out", "uiautomator", "dump", "/dev/tty", timeout=20, check=False).stdout


def current_focus(device: str) -> str:
    return adb(device, "shell", "dumpsys", "window", "windows", timeout=20, check=False).stdout


def extract_reader_content(dump: str) -> str:
    start = dump.find("<?xml")
    end = dump.rfind("</hierarchy>")
    if start < 0 or end < 0:
        return ""
    try:
        root = ET.fromstring(dump[start : end + len("</hierarchy>")])
    except ET.ParseError:
        return ""
    contents = [
        node.attrib.get("content-desc", "")
        for node in root.iter("node")
        if node.attrib.get("package") == PACKAGE
        and node.attrib.get("class") == "io.legado.app.ui.book.read.page.ReadView"
    ]
    return max(contents, key=len, default="")


def wait_until_reader_ready(device: str, marker: str, timeout_seconds: int) -> tuple[bool, str, str]:
    deadline = time.monotonic() + timeout_seconds
    latest = ""
    content = ""
    while time.monotonic() < deadline:
        latest = ui_dump(device)
        content = extract_reader_content(latest)
        if marker in content and PACKAGE in current_focus(device):
            return True, latest, content
        time.sleep(0.5)
    return False, latest, content


def application_pid(device: str) -> str | None:
    result = adb(device, "shell", "pidof", PACKAGE, timeout=10, check=False)
    return result.stdout.strip().split()[0] if result.returncode == 0 and result.stdout.strip() else None


def screen_size(device: str) -> tuple[int, int]:
    output = adb(device, "shell", "wm", "size").stdout
    matches = re.findall(r"(\d+)x(\d+)", output)
    if not matches:
        raise RuntimeError(f"cannot determine screen size: {output.strip()}")
    width, height = matches[-1]
    return int(width), int(height)


def capture_screenshot(device: str, path: Path) -> None:
    with path.open("wb") as output:
        result = subprocess.run(
            ["adb", "-s", device, "exec-out", "screencap", "-p"],
            cwd=PROJECT_ROOT,
            stdout=output,
            stderr=subprocess.PIPE,
            timeout=30,
        )
    if result.returncode != 0:
        path.unlink(missing_ok=True)
        raise RuntimeError(result.stderr.decode(errors="replace"))


def perform_actions(device: str, scenario: dict[str, Any], action_repeat: int) -> int:
    width, height = screen_size(device)
    performed = 0
    for action in scenario["actions"]:
        direction = action["direction"]
        count = action.get("count", 1) * action_repeat
        x = round(width * (0.84 if direction == "next" else 0.16))
        y = round(height * 0.5)
        for _ in range(count):
            adb(device, "shell", "input", "tap", str(x), str(y), timeout=10)
            performed += 1
            time.sleep(0.4)
    return performed


def collect_logcat(
    device: str,
    run_dir: Path,
    app_pid: str | None = None,
    session_id: str | None = None,
) -> str:
    logcat = adb(device, "logcat", "-d", "-v", "threadtime", timeout=45, check=False).stdout
    (run_dir / "logcat.txt").write_text(logcat, encoding="utf-8")
    session_logcat = ""
    if app_pid:
        session_logcat = adb(
            device,
            "logcat",
            "-d",
            f"--pid={app_pid}",
            "-v",
            "threadtime",
            timeout=45,
            check=False,
        ).stdout
    if session_id:
        session_lines = [line for line in logcat.splitlines() if f"[session={session_id}]" in line]
        for line in session_lines:
            if line not in session_logcat:
                session_logcat += line + "\n"
    (run_dir / "session-logcat.txt").write_text(session_logcat, encoding="utf-8")
    crash = extract_crash(logcat, PACKAGE, app_pid)
    (run_dir / "crash.txt").write_text(crash, encoding="utf-8")
    return logcat


def extract_crash(logcat: str, package: str = PACKAGE, app_pid: str | None = None) -> str:
    lines = logcat.splitlines()
    chunks = []
    for index, line in enumerate(lines):
        chunk = lines[index : index + 100]
        if f"ANR in {package}" in line:
            chunks.extend(chunk)
        elif "FATAL EXCEPTION:" in line and any(package in item for item in chunk[:10]):
            chunks.extend(chunk)
        elif "Fatal signal " in line and app_pid and re.search(rf"\b{re.escape(app_pid)}\b", line):
            chunks.extend(chunk)
    if not chunks:
        return ""
    return "\n".join(chunks).rstrip() + "\n"


def parse_failure(crash: str) -> dict[str, Any] | None:
    if not crash:
        return None
    if "ANR in" in crash:
        return {
            "category": "anr",
            "confidence": "high",
            "detectedSignals": ["anr"],
            "stacktraceArtifact": "crash.txt",
        }
    if "Fatal signal " in crash:
        return {
            "category": "native_crash",
            "confidence": "high",
            "detectedSignals": ["fatal_signal"],
            "stacktraceArtifact": "crash.txt",
        }
    exceptions = list(
        re.finditer(r"(Caused by: )?([\w.$]+(?:Exception|Error))(?:: ([^\n]+))?", crash)
    )
    caused_by = [match for match in exceptions if match.group(1)]
    exception = caused_by[-1] if caused_by else (exceptions[0] if exceptions else None)
    failure: dict[str, Any] = {
        "category": "uncaught_exception",
        "confidence": "high",
        "detectedSignals": ["fatal_exception"],
        "stacktraceArtifact": "crash.txt",
    }
    if exception:
        failure["exceptionType"] = exception.group(2).split(".")[-1]
        failure["exceptionConfidence"] = "medium" if exceptions else "low"
        if exception.group(3):
            failure["message"] = exception.group(3).strip()
    frame = re.search(
        r"at (io\.legado\.app\.[\w.$]+)\.([\w$<>]+)\(([^():]+):(\d+)\)",
        crash,
    )
    if frame:
        failure["suspectedFrame"] = {
            "class": frame.group(1),
            "method": frame.group(2),
            "file": frame.group(3),
            "line": int(frame.group(4)),
            "confidence": "medium",
        }
    return failure


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8", errors="replace")).hexdigest()


def make_summary(
    *,
    session_id: str,
    scenario: dict[str, Any],
    fixture: str,
    build: dict[str, Any],
    ready: bool,
    activity_visible: bool,
    content_changed: bool,
    action_count: int,
    failure: dict[str, Any] | None,
) -> tuple[dict[str, Any], int]:
    assertion_values = {
        "reader_ready": ready,
        "reader_activity_visible": activity_visible,
        "reader_content_changed": content_changed,
        "no_fatal_exception": failure is None,
    }
    assertions = [
        {"name": name, "passed": assertion_values[name]}
        for name in scenario["assertions"]
    ]
    if failure is not None:
        status, exit_code = "failed", EXIT_CRASH
    elif not all(item["passed"] for item in assertions):
        status, exit_code = "failed", EXIT_ASSERTION
    else:
        status, exit_code = "passed", 0
    summary: dict[str, Any] = {
        "schemaVersion": 1,
        "status": status,
        "exitCode": exit_code,
        "sessionId": session_id,
        "scenario": scenario["name"],
        "fixture": fixture,
        "build": build,
        "actions": {"performed": action_count},
        "assertions": assertions,
        "artifacts": {
            "metadata": "metadata.json",
            "logcat": "logcat.txt",
            "sessionLogcat": "session-logcat.txt",
            "crash": "crash.txt",
            "beforeScreenshot": "before.png",
            "afterScreenshot": "after.png",
        },
    }
    if failure is not None:
        summary["failure"] = failure
    return summary, exit_code


def error_summary(
    session_id: str,
    scenario_name: str,
    fixture: str,
    exit_code: int,
    category: str,
    message: str,
    build: dict[str, Any] | None = None,
) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "schemaVersion": 1,
        "status": "failed",
        "exitCode": exit_code,
        "sessionId": session_id,
        "scenario": scenario_name,
        "fixture": fixture,
        "failure": {"category": category, "message": message},
        "artifacts": {"metadata": "metadata.json"},
    }
    if build is not None:
        summary["build"] = build
    return summary


def cleanup_device(device: str) -> None:
    adb(device, "shell", "am", "force-stop", PACKAGE, timeout=20, check=False)


def execute(args: argparse.Namespace) -> int:
    try:
        scenario = load_scenario(args.scenario)
        fixture = args.fixture or scenario["fixture"]
        fixture_data = validate_fixture(fixture)
        markers = {chapter["readyMarker"] for chapter in fixture_data["chapters"]}
        if scenario["readyMarker"] not in markers:
            raise ScenarioError("scenario readyMarker is not provided by the fixture")
        if args.action_repeat < 1 or args.action_repeat > 100:
            raise ScenarioError("--action-repeat must be between 1 and 100")
    except ScenarioError as error:
        print(json.dumps({"status": "failed", "exitCode": EXIT_SCENARIO, "error": str(error)}, ensure_ascii=False))
        return EXIT_SCENARIO

    try:
        device = select_device(args.device)
    except (RuntimeError, CommandError, subprocess.TimeoutExpired, OSError) as error:
        session_id, run_dir, _ = create_session(args.scenario, fixture, args.device)
        summary = error_summary(session_id, args.scenario, fixture, EXIT_ENVIRONMENT, "environment", str(error))
        write_json(run_dir / "summary.json", summary)
        print(run_dir / "summary.json")
        return EXIT_ENVIRONMENT

    session_id, run_dir, metadata = create_session(args.scenario, fixture, device)
    build: dict[str, Any] = {"status": "not_started"}
    try:
        build, _ = build_and_install(device, run_dir)
    except (RuntimeError, CommandError, subprocess.TimeoutExpired) as error:
        if isinstance(error, CommandError):
            build = {
                "status": "failed",
                "artifact": "build.log" if (run_dir / "build.log").exists() else "install.log",
            }
        summary = error_summary(session_id, args.scenario, fixture, EXIT_BUILD, "build_or_install", str(error), build)
        cleanup_device(device)
        write_json(run_dir / "summary.json", summary)
        print(run_dir / "summary.json")
        return EXIT_BUILD

    app_pid: str | None = None
    try:
        launch_fixture(device, fixture, session_id)
        app_pid = application_pid(device)
        ready, before_dump, before_content = wait_until_reader_ready(
            device,
            scenario["readyMarker"],
            scenario.get("readyTimeoutSeconds", 30),
        )
        capture_screenshot(device, run_dir / "before.png")
        action_count = perform_actions(device, scenario, args.action_repeat) if ready else 0
        time.sleep(0.5)
        after_dump = ui_dump(device)
        after_content = extract_reader_content(after_dump)
        capture_screenshot(device, run_dir / "after.png")
        focus = current_focus(device)
        activity_visible = PACKAGE in focus and MAIN_ACTIVITY in focus
        content_changed = ready and bool(after_content) and before_content != after_content
        logcat = collect_logcat(device, run_dir, app_pid, session_id)
        failure = parse_failure(extract_crash(logcat, PACKAGE, app_pid))
        metadata.update(
            {
                "finishedAt": dt.datetime.now().astimezone().isoformat(timespec="milliseconds"),
                "actionRepeat": args.action_repeat,
                "uiBeforeSha256": sha256_text(before_dump),
                "uiAfterSha256": sha256_text(after_dump),
                "readerContentBeforeSha256": sha256_text(before_content),
                "readerContentAfterSha256": sha256_text(after_content),
                "appPid": app_pid,
            }
        )
        write_json(run_dir / "metadata.json", metadata)
        summary, exit_code = make_summary(
            session_id=session_id,
            scenario=scenario,
            fixture=fixture,
            build=build,
            ready=ready,
            activity_visible=activity_visible,
            content_changed=content_changed,
            action_count=action_count,
            failure=failure,
        )
    except (RuntimeError, CommandError, subprocess.TimeoutExpired, OSError) as error:
        try:
            collect_logcat(device, run_dir, app_pid, session_id)
        except Exception:
            pass
        summary = error_summary(
            session_id,
            args.scenario,
            fixture,
            EXIT_ENVIRONMENT,
            "environment",
            str(error),
            build,
        )
        exit_code = EXIT_ENVIRONMENT
    finally:
        cleanup_device(device)
    write_json(run_dir / "summary.json", summary)
    print(run_dir / "summary.json")
    return exit_code


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    run_parser = subparsers.add_parser("run", help="build, install, and run one scenario")
    run_parser.add_argument("scenario", help="scenario ID, for example reader/page-turn")
    run_parser.add_argument("--device", help="adb device serial; optional when exactly one is ready")
    run_parser.add_argument("--fixture", help="override the scenario fixture ID")
    run_parser.add_argument("--action-repeat", type=int, default=1, help="repeat each domain action (default: 1)")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    if args.command == "run":
        return execute(args)
    return EXIT_SCENARIO


if __name__ == "__main__":
    raise SystemExit(main())
