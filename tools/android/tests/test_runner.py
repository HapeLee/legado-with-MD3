import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


RUNNER_PATH = Path(__file__).parents[1] / "runner.py"
SPEC = importlib.util.spec_from_file_location("legado_android_runner", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner)


class ScenarioValidationTest(unittest.TestCase):
    def test_repository_scenario_is_valid(self):
        scenario = runner.load_scenario("reader/page-turn")
        self.assertEqual("long-chapter-v1", scenario["fixture"])

    def test_rejects_unknown_action(self):
        scenario = {
            "schemaVersion": 1,
            "name": "reader/page-turn",
            "fixture": "long-chapter-v1",
            "entry": {"type": "reader"},
            "readyMarker": "marker",
            "actions": [{"type": "tap"}],
            "assertions": ["reader_ready"],
        }
        with self.assertRaises(runner.ScenarioError):
            runner.validate_scenario(scenario)

    def test_rejects_missing_fixture(self):
        with self.assertRaises(runner.ScenarioError):
            runner.validate_fixture("missing-v1")

    def test_rejects_invalid_fixture_before_device_work(self):
        fixture = {
            "schemaVersion": 1,
            "id": "broken-v1",
            "resetPolicy": "replace",
            "book": {
                "bookUrl": "debug://book",
                "name": "broken",
                "author": "debug",
                "pageMode": "simulation",
                "startChapter": 0,
                "startPosition": 0,
            },
            "chapters": [{"title": "chapter", "readyMarker": "ready", "paragraph": "text", "repeat": 0}],
        }
        with self.assertRaises(runner.ScenarioError):
            runner.validate_fixture_data(fixture, "broken-v1")


class FailureParsingTest(unittest.TestCase):
    def test_ignores_crash_from_another_package(self):
        logcat = """FATAL EXCEPTION: main
Process: com.example.other, PID: 42
java.lang.IllegalStateException: unrelated
"""
        self.assertEqual("", runner.extract_crash(logcat))

    def test_parses_app_frame_as_inference(self):
        crash = """FATAL EXCEPTION: main
java.lang.IndexOutOfBoundsException: Index 18 out of bounds
    at io.legado.app.ui.book.read.ReadBookViewModel.loadChapter(ReadBookViewModel.kt:418)
"""
        failure = runner.parse_failure(crash)
        self.assertEqual("uncaught_exception", failure["category"])
        self.assertEqual("IndexOutOfBoundsException", failure["exceptionType"])
        self.assertEqual("medium", failure["suspectedFrame"]["confidence"])
        self.assertEqual(418, failure["suspectedFrame"]["line"])

    def test_prefers_deepest_caused_by_as_root_cause_candidate(self):
        crash = """FATAL EXCEPTION: main
java.lang.RuntimeException: wrapper
Caused by: java.lang.IllegalStateException: middle
Caused by: java.lang.IndexOutOfBoundsException: root
"""
        failure = runner.parse_failure(crash)
        self.assertEqual("IndexOutOfBoundsException", failure["exceptionType"])
        self.assertEqual("root", failure["message"])
        self.assertEqual(["fatal_exception"], failure["detectedSignals"])

    def test_crash_has_priority_over_assertion_failure(self):
        scenario = runner.load_scenario("reader/page-turn")
        summary, exit_code = runner.make_summary(
            session_id="test",
            scenario=scenario,
            fixture="long-chapter-v1",
            build={"status": "passed"},
            ready=False,
            activity_visible=False,
            content_changed=False,
            action_count=0,
            failure={"category": "uncaught_exception", "stacktraceArtifact": "crash.txt"},
        )
        self.assertEqual(runner.EXIT_CRASH, exit_code)
        self.assertEqual(runner.EXIT_CRASH, summary["exitCode"])


class CommandDecodingTest(unittest.TestCase):
    def test_replaces_invalid_utf8_in_command_output(self):
        result = runner.run_command(
            [sys.executable, "-c", "import sys; sys.stdout.buffer.write(b'log\\xc0tail')"],
        )
        self.assertEqual("log\ufffdtail", result.stdout)

    @mock.patch.object(runner, "cleanup_device")
    @mock.patch.object(runner, "run_command")
    def test_install_retries_with_streaming_after_timeout(self, run_command, cleanup_device):
        run_command.side_effect = [
            subprocess.TimeoutExpired(["adb", "install"], 120),
            subprocess.CompletedProcess(["adb", "install"], 0, "Success\n"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            mode = runner.install_apk("device", Path("app.apk"), Path(directory))
            install_log = (Path(directory) / "install.log").read_text(encoding="utf-8")
        self.assertEqual("streaming_retry", mode)
        self.assertIn("timeout=120s", install_log)
        cleanup_device.assert_called_once_with("device")


class ReaderContentTest(unittest.TestCase):
    def test_extracts_read_view_content_only(self):
        dump = """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
<hierarchy rotation="0">
  <node class="android.widget.TextView" package="io.legato.kazusa.debug" content-desc="22:30" />
  <node class="io.legado.app.ui.book.read.page.ReadView" package="io.legato.kazusa.debug" content-desc="LEGADO_DEBUG_LONG_CHAPTER_V1 page body" />
</hierarchy>
UI hierarchy dumped to: /dev/tty
"""
        self.assertEqual(
            "LEGADO_DEBUG_LONG_CHAPTER_V1 page body",
            runner.extract_reader_content(dump),
        )

    def test_returns_empty_when_read_view_is_absent(self):
        dump = """<?xml version="1.0"?><hierarchy><node class="android.view.View" /></hierarchy>"""
        self.assertEqual("", runner.extract_reader_content(dump))


if __name__ == "__main__":
    unittest.main()
