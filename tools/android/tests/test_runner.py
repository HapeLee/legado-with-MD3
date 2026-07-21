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

    def test_bookshelf_scenario_is_valid(self):
        scenario = runner.load_scenario("bookshelf/open-reader-back")
        self.assertEqual("bookshelf", scenario["entry"]["type"])

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


class CapabilityTest(unittest.TestCase):
    def test_lists_registered_entries_actions_assertions(self):
        caps = runner.build_capabilities()
        self.assertEqual(["turn_page"], caps["entries"]["reader"]["allowedActions"])
        self.assertEqual(
            ["open_fixture_book", "press_back"],
            caps["entries"]["bookshelf"]["allowedActions"],
        )
        self.assertIn("turn_page", caps["actions"])
        self.assertIn("no_fatal_exception", caps["assertions"])

    def test_list_command_returns_zero(self):
        self.assertEqual(0, runner.main(["list"]))

    def test_parses_skip_build_flag(self):
        args = runner.parse_args(["run", "reader/page-turn", "--skip-build"])
        self.assertTrue(args.skip_build)

    def test_run_without_skip_build_defaults_false(self):
        args = runner.parse_args(["run", "reader/page-turn"])
        self.assertFalse(args.skip_build)


class PreferencesTest(unittest.TestCase):
    BASE = {
        "schemaVersion": 1,
        "name": "bookshelf/miuix-hidden-statusbar",
        "fixture": "long-chapter-v1",
        "entry": {"type": "bookshelf"},
        "readyMarker": "marker",
        "record": True,
        "actions": [{"type": "open_fixture_book"}, {"type": "press_back"}],
        "assertions": ["bookshelf_ready"],
    }

    def test_repository_miuix_scenario_is_valid(self):
        scenario = runner.load_scenario("bookshelf/miuix-hidden-statusbar")
        self.assertTrue(scenario["record"])
        self.assertEqual("miuix", scenario["preferences"]["composeEngine"])
        self.assertTrue(scenario["preferences"]["hideStatusBar"])

    def test_accepts_string_and_boolean_preferences(self):
        scenario = {**self.BASE, "preferences": {"composeEngine": "miuix", "hideStatusBar": True}}
        runner.validate_scenario(scenario)

    def test_rejects_non_primitive_preference_value(self):
        scenario = {**self.BASE, "preferences": {"menuAlpha": 50}}
        with self.assertRaises(runner.ScenarioError):
            runner.validate_scenario(scenario)

    def test_rejects_invalid_preference_key(self):
        scenario = {**self.BASE, "preferences": {"bad-key": "x"}}
        with self.assertRaises(runner.ScenarioError):
            runner.validate_scenario(scenario)

    def test_rejects_non_boolean_record(self):
        scenario = {**self.BASE, "record": "yes"}
        with self.assertRaises(runner.ScenarioError):
            runner.validate_scenario(scenario)


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
    def test_extracts_reader_pages_from_logcat(self):
        logcat = (
            "07-21 23:52 26863 26863 I LegadoDebug: [session=x] FIXTURE_READY fixture=y entry=reader\n"
            "07-21 23:52 26863 26863 I LegadoDebug: READER_PAGE LEGADO_DEBUG_LONG_CHAPTER_V1 page one\n"
            "07-21 23:52 26863 26863 I LegadoDebug: READER_PAGE page two body\n"
        )
        self.assertEqual(
            ["LEGADO_DEBUG_LONG_CHAPTER_V1 page one", "page two body"],
            runner.extract_reader_pages(logcat),
        )

    def test_returns_empty_when_no_reader_page_logged(self):
        self.assertEqual([], runner.extract_reader_pages("nothing here\n"))

    @mock.patch.object(runner.time, "sleep")
    @mock.patch.object(runner, "reader_current_page")
    def test_waits_until_reader_content_changes(self, reader_current_page, _sleep):
        reader_current_page.side_effect = ["before", "after"]
        changed, latest, content = runner.wait_until_reader_content_changed("device", "before", 5)
        self.assertTrue(changed)
        self.assertEqual("after", content)
        self.assertEqual("after", latest)
        self.assertEqual(2, reader_current_page.call_count)

    def test_finds_clickable_bookshelf_item_and_center(self):
        dump = """<?xml version="1.0"?><hierarchy>
<node class="android.view.View" package="io.legato.kazusa.debug" clickable="true"
 content-desc="调试夹具：长章节, Legado Debug" bounds="[100,200][300,600]" />
</hierarchy>"""
        node = runner.find_node_by_content_prefix(dump, "调试夹具：长章节")
        self.assertIsNotNone(node)
        self.assertEqual((200, 400), runner.node_center(node))


class SessionLogTest(unittest.TestCase):
    @mock.patch.object(runner, "adb")
    def test_collects_each_observed_application_pid(self, adb):
        adb.side_effect = [
            subprocess.CompletedProcess([], 0, "full log\n[session=s1] ready\n"),
            subprocess.CompletedProcess([], 0, "pid 10 log\n"),
            subprocess.CompletedProcess([], 0, "pid 11 log\n"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            runner.collect_logcat("device", Path(directory), ["10", "11", "10"], "s1")
            session_log = (Path(directory) / "session-logcat.txt").read_text(encoding="utf-8")
        self.assertIn("--- pid=10 ---", session_log)
        self.assertIn("--- pid=11 ---", session_log)
        self.assertIn("[session=s1] ready", session_log)
        self.assertEqual(3, adb.call_count)


class DeviceStateTest(unittest.TestCase):
    @mock.patch.object(runner, "adb")
    def test_detects_awake_device(self, adb):
        adb.return_value = subprocess.CompletedProcess([], 0, "  mWakefulness=Awake\n")
        self.assertTrue(runner.device_is_awake("device"))

    @mock.patch.object(runner, "adb")
    def test_detects_visible_keyguard(self, adb):
        adb.return_value = subprocess.CompletedProcess([], 0, "KeyguardServiceDelegate\n  showing=true\n")
        self.assertTrue(runner.keyguard_is_showing("device"))


if __name__ == "__main__":
    unittest.main()
