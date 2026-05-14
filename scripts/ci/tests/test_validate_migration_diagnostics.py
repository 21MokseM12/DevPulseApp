import json
import tempfile
import unittest
from pathlib import Path

from scripts.ci.validate_migration_diagnostics import build_markdown
from scripts.ci.validate_migration_diagnostics import run_checks


class ValidateMigrationDiagnosticsTest(unittest.TestCase):
    def test_run_checks_passes_when_required_artifacts_exist(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            artifacts_dir = Path(temp_dir) / "artifacts"
            migration_artifacts = artifacts_dir / "quality-instrumented-migration"
            reports_dir = migration_artifacts / "app" / "build" / "reports" / "androidTests"
            outputs_dir = migration_artifacts / "app" / "build" / "outputs" / "androidTest-results" / "connected"

            reports_dir.mkdir(parents=True)
            outputs_dir.mkdir(parents=True)

            (migration_artifacts / "gate-instrumented-migration.json").write_text(
                json.dumps(
                    {
                        "gate_id": "instrumented-migration",
                        "gate_name": "Instrumented Migration (Room)",
                        "status": "success",
                    }
                ),
                encoding="utf-8",
            )
            (reports_dir / "migration-diagnostics.md").write_text("# diagnostics", encoding="utf-8")
            (reports_dir / "logcat-migration.txt").write_text("log line", encoding="utf-8")
            (outputs_dir / "TEST-com.devpulse.app.data.local.db.AppDatabaseMigrationTest.xml").write_text(
                "<testsuite />",
                encoding="utf-8",
            )

            checks = run_checks(artifacts_dir)

            self.assertTrue(all(item.is_ok for item in checks))
            self.assertIn("Checks failed: **0**", build_markdown(checks))

    def test_run_checks_fails_when_diagnostics_files_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            artifacts_dir = Path(temp_dir) / "artifacts"
            migration_artifacts = artifacts_dir / "quality-instrumented-migration"
            migration_artifacts.mkdir(parents=True)
            (migration_artifacts / "gate-instrumented-migration.json").write_text(
                json.dumps({"status": "success"}),
                encoding="utf-8",
            )

            checks = run_checks(artifacts_dir)
            failed_checks = [item for item in checks if not item.is_ok]

            self.assertGreaterEqual(len(failed_checks), 1)
            self.assertIn("Checks failed", build_markdown(checks))


if __name__ == "__main__":
    unittest.main()
