#!/usr/bin/env python3
"""Deterministic Android debug scenario runner for Legado."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from typing import Any, Callable, Sequence
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

# Capability registries. New bug scenarios extend the tool by registering an
# entry / action / assertion below, never by editing execute() or
# validate_scenario(). See the classes near the end of this module.
ENTRIES: dict[str, "Entry"] = {}
ACTIONS: dict[str, "Action"] = {}
ASSERTIONS: dict[str, Callable[["Context"], bool]] = {}


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
    change_timeout = scenario.get("contentChangeTimeoutSeconds", 5)
    if not isinstance(change_timeout, int) or change_timeout < 1 or change_timeout > 30:
        raise ScenarioError("contentChangeTimeoutSeconds must be between 1 and 30")
    record = scenario.get("record", False)
    if not isinstance(record, bool):
        raise ScenarioError("record must be a boolean")
    validate_preferences(scenario.get("preferences"))
    entry = scenario.get("entry")
    if not isinstance(entry, dict) or entry.get("type") not in ENTRIES:
        raise ScenarioError(f"entry.type must be one of: {', '.join(sorted(ENTRIES))}")
    entry_handler = ENTRIES[entry["type"]]

    actions = scenario.get("actions")
    if not isinstance(actions, list) or not actions:
        raise ScenarioError("actions must be a non-empty array")
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            raise ScenarioError(f"actions[{index}] must be an object")
        action_type = action.get("type")
        if action_type not in entry_handler.allowed_actions:
            raise ScenarioError(f"actions[{index}] has an unsupported type")
        ACTIONS[action_type].validate(action, index)

    assertions = scenario.get("assertions")
    if not isinstance(assertions, list) or not assertions:
        raise ScenarioError("assertions must be a non-empty array")
    unknown = set(assertions) - set(ASSERTIONS)
    if unknown:
        raise ScenarioError(f"unsupported assertions: {', '.join(sorted(unknown))}")


def validate_preferences(preferences: Any) -> None:
    # Scenario-declared app settings applied before launch (theme engine,
    # reader toggles, ...). Restricted to String/Boolean so the debug Activity
    # can apply them unambiguously via AppConfigStore.
    if preferences is None:
        return
    if not isinstance(preferences, dict) or not preferences:
        raise ScenarioError("preferences must be a non-empty object when present")
    for key, value in preferences.items():
        if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9]*", key):
            raise ScenarioError(f"invalid preference key: {key}")
        if not isinstance(value, (str, bool)):
            raise ScenarioError(f"preference {key} must be a string or boolean")


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


def launch_fixture(
    device: str,
    fixture: str,
    entry: str,
    session_id: str,
    preferences: dict[str, Any] | None = None,
) -> None:
    adb(device, "shell", "am", "force-stop", PACKAGE)
    adb(device, "logcat", "-c")
    component = f"{PACKAGE}/{DEBUG_ACTIVITY}"
    extras = [
        "--es", "fixture", fixture,
        "--es", "session", session_id,
        "--es", "entry", entry,
    ]
    if preferences:
        # base64 so the JSON survives `adb shell am` re-parsing on the device
        # (raw JSON gets quote-stripped and brace-expanded by the device shell).
        payload = json.dumps(preferences, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        extras += ["--es", "preferencesB64", base64.b64encode(payload).decode("ascii")]
    result = adb(
        device,
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        component,
        *extras,
        timeout=60,
        check=False,
    )
    if result.returncode != 0 or "Error:" in result.stdout:
        raise CommandError(["adb", "shell", "am", "start", component], result)


def ui_dump(device: str) -> str:
    return adb(device, "exec-out", "uiautomator", "dump", "/dev/tty", timeout=20, check=False).stdout


def window_state(device: str) -> str:
    # Full `dumpsys window windows` output. Callers substring-match against it,
    # so a hit means the activity is present in the window stack, not strictly
    # the foreground window.
    return adb(device, "shell", "dumpsys", "window", "windows", timeout=20, check=False).stdout


def device_is_awake(device: str) -> bool:
    output = adb(device, "shell", "dumpsys", "power", timeout=20, check=False).stdout
    return "mWakefulness=Awake" in output


def keyguard_is_showing(device: str) -> bool:
    output = adb(device, "shell", "dumpsys", "window", "policy", timeout=20, check=False).stdout
    match = re.search(r"^\s+showing=(true|false)\b", output, re.MULTILINE)
    return match is not None and match.group(1) == "true"


def device_ready_for_ui(device: str) -> bool:
    return device_is_awake(device) and not keyguard_is_showing(device)


def prepare_device(device: str) -> dict[str, Any]:
    state = {
        "wasAwake": device_is_awake(device),
        "wasKeyguardShowing": keyguard_is_showing(device),
        "stayOnWhilePluggedIn": adb(
            device,
            "shell",
            "settings",
            "get",
            "global",
            "stay_on_while_plugged_in",
            timeout=20,
            check=False,
        ).stdout.strip(),
    }
    adb(
        device,
        "shell",
        "settings",
        "put",
        "global",
        "stay_on_while_plugged_in",
        "2",
        timeout=20,
    )
    if not state["wasAwake"]:
        adb(device, "shell", "input", "keyevent", "KEYCODE_WAKEUP", timeout=20)
    if keyguard_is_showing(device):
        adb(device, "shell", "wm", "dismiss-keyguard", timeout=20, check=False)
        time.sleep(0.2)
    if keyguard_is_showing(device):
        adb(device, "shell", "input", "keyevent", "82", timeout=20, check=False)
        time.sleep(0.5)
    if not device_ready_for_ui(device):
        restore_device(device, state)
        raise RuntimeError("device could not be awakened and unlocked for UI automation")
    return state


def restore_device(device: str, state: dict[str, Any] | None) -> None:
    if state is None:
        return
    stay_on = state.get("stayOnWhilePluggedIn")
    if stay_on in {"", "null", None}:
        adb(
            device,
            "shell",
            "settings",
            "delete",
            "global",
            "stay_on_while_plugged_in",
            timeout=20,
            check=False,
        )
    else:
        adb(
            device,
            "shell",
            "settings",
            "put",
            "global",
            "stay_on_while_plugged_in",
            str(stay_on),
            timeout=20,
            check=False,
        )
    if state.get("wasKeyguardShowing"):
        adb(device, "shell", "input", "keyevent", "KEYCODE_SLEEP", timeout=20, check=False)
        if state.get("wasAwake"):
            adb(device, "shell", "input", "keyevent", "KEYCODE_WAKEUP", timeout=20, check=False)
    elif not state.get("wasAwake"):
        adb(device, "shell", "input", "keyevent", "KEYCODE_SLEEP", timeout=20, check=False)


def parse_ui_nodes(dump: str) -> list[dict[str, str]]:
    start = dump.find("<?xml")
    end = dump.rfind("</hierarchy>")
    if start < 0 or end < 0:
        return []
    try:
        root = ET.fromstring(dump[start : end + len("</hierarchy>")])
    except ET.ParseError:
        return []
    return [node.attrib for node in root.iter("node")]


READER_PAGE_TAG = "READER_PAGE "


def extract_reader_pages(logcat: str) -> list[str]:
    # ReadView logs "READER_PAGE <one-line page text>" on every render (debug
    # build only). This is the device-independent reader signal — uiautomator
    # does not expose the ReadView node on all devices.
    pages = []
    for line in logcat.splitlines():
        index = line.find(READER_PAGE_TAG)
        if index >= 0:
            pages.append(line[index + len(READER_PAGE_TAG):].strip())
    return pages


def reader_current_page(device: str) -> str:
    logcat = adb(device, "logcat", "-d", "-s", "LegadoDebug:I", timeout=20, check=False).stdout
    pages = extract_reader_pages(logcat)
    return pages[-1] if pages else ""


def find_node_by_content_prefix(dump: str, prefix: str) -> dict[str, str] | None:
    # The bookshelf item's label lives on a node whose clickable flag is false
    # (the clickable modifier sits on an ancestor). Match by content-desc and
    # tap the label's center; the clickable ancestor is under that point.
    candidates = [
        node
        for node in parse_ui_nodes(dump)
        if node.get("package") == PACKAGE
        and node.get("content-desc", "").startswith(prefix)
    ]
    return candidates[0] if candidates else None


def node_center(node: dict[str, str]) -> tuple[int, int]:
    bounds = node.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)]\[(\d+),(\d+)]", bounds)
    if match is None:
        raise RuntimeError(f"invalid accessibility bounds: {bounds}")
    left, top, right, bottom = map(int, match.groups())
    return (left + right) // 2, (top + bottom) // 2


def wait_until_node_present(
    device: str,
    content_prefix: str,
    timeout_seconds: int,
) -> tuple[bool, str, dict[str, str] | None]:
    deadline = time.monotonic() + timeout_seconds
    latest = ""
    node = None
    while time.monotonic() < deadline:
        latest = ui_dump(device)
        node = find_node_by_content_prefix(latest, content_prefix)
        if node is not None and device_ready_for_ui(device):
            return True, latest, node
        time.sleep(0.5)
    return False, latest, node


def wait_until_reader_ready(device: str, marker: str, timeout_seconds: int) -> tuple[bool, str, str]:
    deadline = time.monotonic() + timeout_seconds
    content = ""
    while time.monotonic() < deadline:
        content = reader_current_page(device)
        if marker in content and PACKAGE in window_state(device) and device_ready_for_ui(device):
            return True, content, content
        time.sleep(0.5)
    return False, content, content


def wait_until_reader_content_changed(
    device: str,
    previous_content: str,
    timeout_seconds: int,
) -> tuple[bool, str, str]:
    deadline = time.monotonic() + timeout_seconds
    content = ""
    while time.monotonic() < deadline:
        content = reader_current_page(device)
        if content and content != previous_content:
            return True, content, content
        time.sleep(0.2)
    return False, content, content


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


class ScreenRecorder:
    """Optional screenrecord wrapper for capturing transient UI (animations).

    Best-effort: any failure to start, stop, or pull leaves the run unaffected
    and simply produces no video artifact.
    """

    REMOTE = "/sdcard/legado-debug-transition.mp4"

    def __init__(self, device: str):
        self.device = device
        self.process: subprocess.Popen[bytes] | None = None
        self.saved: str | None = None
        self._stopped = False

    def start(self) -> None:
        adb(self.device, "shell", "rm", "-f", self.REMOTE, timeout=15, check=False)
        self.process = subprocess.Popen(
            ["adb", "-s", self.device, "shell", "screenrecord", "--time-limit", "60", self.REMOTE],
            cwd=PROJECT_ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        time.sleep(0.5)

    def stop(self, run_dir: Path, name: str = "transition.mp4") -> str | None:
        if self.process is None or self._stopped:
            return self.saved
        self._stopped = True
        # SIGINT lets screenrecord finalize a playable mp4 before it exits.
        pids = adb(self.device, "shell", "pidof", "screenrecord", timeout=10, check=False).stdout.split()
        for pid in pids:
            adb(self.device, "shell", "kill", "-INT", pid, timeout=10, check=False)
        try:
            self.process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            self.process.kill()
        pull = subprocess.run(
            ["adb", "-s", self.device, "pull", self.REMOTE, str(run_dir / name)],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True,
            timeout=60,
        )
        adb(self.device, "shell", "rm", "-f", self.REMOTE, timeout=15, check=False)
        if pull.returncode == 0 and (run_dir / name).is_file():
            self.saved = name
        return self.saved


def collect_logcat(
    device: str,
    run_dir: Path,
    app_pids: Sequence[str] = (),
    session_id: str | None = None,
) -> str:
    logcat = adb(device, "logcat", "-d", "-v", "threadtime", timeout=45, check=False).stdout
    (run_dir / "logcat.txt").write_text(logcat, encoding="utf-8")
    session_logcat = ""
    unique_pids = list(dict.fromkeys(pid for pid in app_pids if pid))
    for pid in unique_pids:
        pid_logcat = adb(
            device,
            "logcat",
            "-d",
            f"--pid={pid}",
            "-v",
            "threadtime",
            timeout=45,
            check=False,
        ).stdout
        session_logcat += f"--- pid={pid} ---\n{pid_logcat}"
    if session_id:
        session_lines = [line for line in logcat.splitlines() if f"[session={session_id}]" in line]
        for line in session_lines:
            if line not in session_logcat:
                session_logcat += line + "\n"
    (run_dir / "session-logcat.txt").write_text(session_logcat, encoding="utf-8")
    crash = extract_crash(logcat, PACKAGE, unique_pids)
    (run_dir / "crash.txt").write_text(crash, encoding="utf-8")
    return logcat


def extract_crash(
    logcat: str,
    package: str = PACKAGE,
    app_pids: Sequence[str] = (),
) -> str:
    lines = logcat.splitlines()
    chunks = []
    for index, line in enumerate(lines):
        chunk = lines[index : index + 100]
        if f"ANR in {package}" in line:
            chunks.extend(chunk)
        elif "FATAL EXCEPTION:" in line and any(package in item for item in chunk[:10]):
            chunks.extend(chunk)
        elif "Fatal signal " in line and any(
            re.search(rf"\b{re.escape(pid)}\b", line) for pid in app_pids
        ):
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
    return make_summary_from_assertions(
        session_id=session_id,
        scenario=scenario,
        fixture=fixture,
        build=build,
        assertion_values=assertion_values,
        action_count=action_count,
        failure=failure,
    )


def make_summary_from_assertions(
    *,
    session_id: str,
    scenario: dict[str, Any],
    fixture: str,
    build: dict[str, Any],
    assertion_values: dict[str, bool],
    action_count: int,
    failure: dict[str, Any] | None,
    extra_artifacts: dict[str, str] | None = None,
) -> tuple[dict[str, Any], int]:
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
        },
    }
    if failure is not None:
        summary["failure"] = failure
    if extra_artifacts:
        summary["artifacts"].update(extra_artifacts)
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


# --------------------------------------------------------------------------- #
# Capability registry: entries, actions, assertions.
#
# A scenario is fully described by data (JSON). Each domain action and each
# assertion is a small handler registered here. Adding a bug scenario means
# adding a handler + a JSON file, not editing execute() / validate_scenario().
# Handlers only *record* raw observations into the Context; assertions are pure
# reads of those observations.
# --------------------------------------------------------------------------- #


class Context:
    """Mutable per-run state shared across entry, actions, and assertions."""

    def __init__(
        self,
        device: str,
        run_dir: Path,
        scenario: dict[str, Any],
        fixture_data: dict[str, Any],
        metadata: dict[str, Any],
        action_repeat: int,
    ):
        self.device = device
        self.run_dir = run_dir
        self.scenario = scenario
        self.fixture_data = fixture_data
        self.metadata = metadata
        self.action_repeat = action_repeat
        self.app_pids: list[str] = []
        self.records: dict[str, Any] = {}
        self.state: dict[str, Any] = {}
        self.artifacts: dict[str, str] = {}
        self.action_count = 0
        self.assertion_values: dict[str, bool] = {}
        self.failure: dict[str, Any] | None = None

    def note_pid(self) -> None:
        pid = application_pid(self.device)
        if pid and pid not in self.app_pids:
            self.app_pids.append(pid)

    def screenshot(self, name: str, filename: str) -> None:
        capture_screenshot(self.device, self.run_dir / filename)
        self.artifacts[name] = filename


class Entry:
    type: str = ""
    allowed_actions: set[str] = set()

    def prepare(self, ctx: Context) -> None:
        raise NotImplementedError

    def finalize(self, ctx: Context) -> None:
        raise NotImplementedError


class Action:
    name: str = ""
    params: dict[str, Any] = {}

    def validate(self, action: dict[str, Any], index: int) -> None:
        pass

    def run(self, ctx: Context, action: dict[str, Any]) -> None:
        raise NotImplementedError


def register_entry(name: str) -> Callable[[type], type]:
    def decorate(cls: type) -> type:
        instance = cls()
        instance.type = name
        ENTRIES[name] = instance
        return cls

    return decorate


def register_action(name: str) -> Callable[[type], type]:
    def decorate(cls: type) -> type:
        instance = cls()
        instance.name = name
        ACTIONS[name] = instance
        return cls

    return decorate


@register_entry("reader")
class ReaderEntry(Entry):
    allowed_actions = {"turn_page"}

    def prepare(self, ctx: Context) -> None:
        ready, before_dump, before_content = wait_until_reader_ready(
            ctx.device,
            ctx.scenario["readyMarker"],
            ctx.scenario.get("readyTimeoutSeconds", 30),
        )
        ctx.note_pid()
        ctx.screenshot("beforeScreenshot", "before.png")
        ctx.records["reader_ready"] = ready
        ctx.records["reader_content_changed"] = False
        ctx.records["content_change_duration_ms"] = 0
        ctx.records["ui_before"] = before_dump
        ctx.records["ui_after"] = before_dump
        ctx.records["reader_content_before"] = before_content
        ctx.records["reader_content_after"] = before_content
        ctx.state["reader_content"] = before_content

    def finalize(self, ctx: Context) -> None:
        ctx.screenshot("afterScreenshot", "after.png")
        windows = window_state(ctx.device)
        # Presence of MainActivity in the window stack; the reader runs as a
        # route inside MainActivity in this fork.
        visible = PACKAGE in windows and MAIN_ACTIVITY in windows and device_ready_for_ui(ctx.device)
        ctx.records["reader_activity_visible"] = visible
        ctx.metadata.update(
            {
                "actionRepeat": ctx.action_repeat,
                "uiBeforeSha256": sha256_text(ctx.records["ui_before"]),
                "uiAfterSha256": sha256_text(ctx.records["ui_after"]),
                "readerContentBeforeSha256": sha256_text(ctx.records["reader_content_before"]),
                "readerContentAfterSha256": sha256_text(ctx.records["reader_content_after"]),
                "contentChangeDurationMs": ctx.records["content_change_duration_ms"],
            }
        )


@register_entry("bookshelf")
class BookshelfEntry(Entry):
    allowed_actions = {"open_fixture_book", "press_back"}

    def prepare(self, ctx: Context) -> None:
        book_name = ctx.fixture_data["book"]["name"]
        ready, before_dump, book_node = wait_until_node_present(
            ctx.device,
            book_name,
            ctx.scenario.get("readyTimeoutSeconds", 30),
        )
        ctx.note_pid()
        ctx.screenshot("beforeScreenshot", "before.png")
        ctx.records["bookshelf_ready"] = ready
        ctx.records["reader_opened"] = False
        ctx.records["returned_to_bookshelf"] = False
        ctx.records["ui_before"] = before_dump
        ctx.records["ui_after"] = before_dump
        ctx.records["reader_dump"] = ""
        ctx.records["reader_content"] = ""
        ctx.state["book_name"] = book_name
        ctx.state["book_node"] = book_node
        ctx.state["returned_node"] = None

    def finalize(self, ctx: Context) -> None:
        ctx.screenshot("afterScreenshot", "after.png")
        book_node = ctx.state.get("book_node")
        returned_node = ctx.state.get("returned_node")
        ctx.metadata.update(
            {
                "uiBeforeSha256": sha256_text(ctx.records["ui_before"]),
                "readerUiSha256": sha256_text(ctx.records["reader_dump"]),
                "uiAfterSha256": sha256_text(ctx.records["ui_after"]),
                "readerContentSha256": sha256_text(ctx.records["reader_content"]),
                "bookshelfItemBefore": book_node.get("content-desc") if book_node else None,
                "bookshelfItemAfter": returned_node.get("content-desc") if returned_node else None,
            }
        )


@register_action("turn_page")
class TurnPage(Action):
    params = {"direction": ["next", "previous"], "count": "1-100"}

    def validate(self, action: dict[str, Any], index: int) -> None:
        if action.get("direction") not in {"next", "previous"}:
            raise ScenarioError(f"actions[{index}].direction must be next or previous")
        count = action.get("count", 1)
        if not isinstance(count, int) or count < 1 or count > 100:
            raise ScenarioError(f"actions[{index}].count must be between 1 and 100")

    def run(self, ctx: Context, action: dict[str, Any]) -> None:
        if not ctx.records.get("reader_ready"):
            return
        width, height = screen_size(ctx.device)
        direction = action["direction"]
        count = action.get("count", 1) * ctx.action_repeat
        x = round(width * (0.84 if direction == "next" else 0.16))
        y = round(height * 0.5)
        for _ in range(count):
            adb(ctx.device, "shell", "input", "tap", str(x), str(y), timeout=10)
            ctx.action_count += 1
            time.sleep(0.4)
        started = time.monotonic()
        changed, after_dump, after_content = wait_until_reader_content_changed(
            ctx.device,
            ctx.state["reader_content"],
            ctx.scenario.get("contentChangeTimeoutSeconds", 5),
        )
        ctx.records["content_change_duration_ms"] = round((time.monotonic() - started) * 1000)
        if changed:
            ctx.records["reader_content_changed"] = True
            ctx.state["reader_content"] = after_content
        ctx.records["reader_content_after"] = after_content
        ctx.records["ui_after"] = after_dump


@register_action("open_fixture_book")
class OpenFixtureBook(Action):
    def run(self, ctx: Context, action: dict[str, Any]) -> None:
        book_node = ctx.state.get("book_node")
        if not (ctx.records.get("bookshelf_ready") and book_node is not None):
            return
        x, y = node_center(book_node)
        adb(ctx.device, "shell", "input", "tap", str(x), str(y), timeout=10)
        ctx.action_count += 1
        opened, reader_dump, reader_content = wait_until_reader_ready(
            ctx.device,
            ctx.scenario["readyMarker"],
            ctx.scenario.get("readyTimeoutSeconds", 30),
        )
        ctx.records["reader_opened"] = opened
        ctx.records["reader_dump"] = reader_dump
        ctx.records["reader_content"] = reader_content
        ctx.screenshot("readerScreenshot", "reader.png")


@register_action("press_back")
class PressBack(Action):
    def run(self, ctx: Context, action: dict[str, Any]) -> None:
        if not ctx.records.get("reader_opened"):
            return
        adb(ctx.device, "shell", "input", "keyevent", "BACK", timeout=10)
        ctx.action_count += 1
        returned, after_dump, returned_node = wait_until_node_present(
            ctx.device,
            ctx.state["book_name"],
            ctx.scenario.get("readyTimeoutSeconds", 30),
        )
        ctx.records["returned_to_bookshelf"] = returned
        ctx.records["ui_after"] = after_dump
        ctx.state["returned_node"] = returned_node


ASSERTIONS.update(
    {
        "reader_ready": lambda ctx: bool(ctx.records.get("reader_ready")),
        "reader_activity_visible": lambda ctx: bool(ctx.records.get("reader_activity_visible")),
        "reader_content_changed": lambda ctx: bool(ctx.records.get("reader_content_changed")),
        "bookshelf_ready": lambda ctx: bool(ctx.records.get("bookshelf_ready")),
        "reader_opened": lambda ctx: bool(ctx.records.get("reader_opened")),
        "returned_to_bookshelf": lambda ctx: bool(ctx.records.get("returned_to_bookshelf")),
        "no_fatal_exception": lambda ctx: ctx.failure is None,
    }
)


def build_capabilities() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "entries": {
            name: {"allowedActions": sorted(handler.allowed_actions)}
            for name, handler in sorted(ENTRIES.items())
        },
        "actions": {
            name: {"params": handler.params}
            for name, handler in sorted(ACTIONS.items())
        },
        "assertions": sorted(ASSERTIONS),
    }


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
        if scenario["entry"]["type"] == "bookshelf" and args.action_repeat != 1:
            raise ScenarioError("--action-repeat is only supported by reader scenarios")
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
    device_state: dict[str, Any] | None = None
    try:
        device_state = prepare_device(device)
        metadata["deviceStateBefore"] = device_state
        write_json(run_dir / "metadata.json", metadata)
    except (RuntimeError, CommandError, subprocess.TimeoutExpired, OSError) as error:
        summary = error_summary(session_id, args.scenario, fixture, EXIT_ENVIRONMENT, "environment", str(error))
        write_json(run_dir / "summary.json", summary)
        print(run_dir / "summary.json")
        return EXIT_ENVIRONMENT

    build: dict[str, Any] = {"status": "not_started"}
    if args.skip_build:
        build = {"status": "skipped"}
    else:
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
            restore_device(device, device_state)
            write_json(run_dir / "summary.json", summary)
            print(run_dir / "summary.json")
            return EXIT_BUILD

    entry_handler = ENTRIES[scenario["entry"]["type"]]
    ctx = Context(device, run_dir, scenario, fixture_data, metadata, args.action_repeat)
    recorder = ScreenRecorder(device) if scenario.get("record") else None
    try:
        launch_fixture(device, fixture, entry_handler.type, session_id, scenario.get("preferences"))
        ctx.note_pid()
        entry_handler.prepare(ctx)
        if recorder is not None:
            recorder.start()
        for action in scenario["actions"]:
            ACTIONS[action["type"]].run(ctx, action)
        if recorder is not None:
            time.sleep(0.5)  # tail so the settle after the last action is captured
            video = recorder.stop(run_dir)
            if video:
                ctx.artifacts["transition"] = video
        entry_handler.finalize(ctx)
        ctx.note_pid()
        logcat = collect_logcat(device, run_dir, ctx.app_pids, session_id)
        ctx.failure = parse_failure(extract_crash(logcat, PACKAGE, ctx.app_pids))
        for name in scenario["assertions"]:
            ctx.assertion_values[name] = ASSERTIONS[name](ctx)
        metadata.update(
            {
                "finishedAt": dt.datetime.now().astimezone().isoformat(timespec="milliseconds"),
                "appPids": ctx.app_pids,
            }
        )
        write_json(run_dir / "metadata.json", metadata)
        summary, exit_code = make_summary_from_assertions(
            session_id=session_id,
            scenario=scenario,
            fixture=fixture,
            build=build,
            assertion_values=ctx.assertion_values,
            action_count=ctx.action_count,
            failure=ctx.failure,
            extra_artifacts=ctx.artifacts,
        )
    except (RuntimeError, CommandError, subprocess.TimeoutExpired, OSError) as error:
        try:
            ctx.note_pid()
            collect_logcat(device, run_dir, ctx.app_pids, session_id)
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
        if recorder is not None:
            recorder.stop(run_dir)
        cleanup_device(device)
        restore_device(device, device_state)
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
    run_parser.add_argument(
        "--skip-build",
        action="store_true",
        help="reuse the installed debug APK; skip Gradle build and install",
    )
    subparsers.add_parser("list", help="print supported entries, actions, and assertions")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    if args.command == "run":
        return execute(args)
    if args.command == "list":
        print(json.dumps(build_capabilities(), ensure_ascii=False, indent=2))
        return 0
    return EXIT_SCENARIO


if __name__ == "__main__":
    raise SystemExit(main())
