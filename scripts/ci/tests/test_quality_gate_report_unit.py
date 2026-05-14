import json
import tempfile
import unittest
from pathlib import Path

from scripts.ci.quality_gate_report import build_report
from scripts.ci.quality_gate_report import load_results


class QualityGateReportUnitTest(unittest.TestCase):
    def test_load_results_sorts_by_gate_name(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            artifacts_dir = Path(temp_dir)
            (artifacts_dir / "quality-unit").mkdir(parents=True)
            (artifacts_dir / "quality-assemble").mkdir(parents=True)

            (artifacts_dir / "quality-unit" / "gate-unit.json").write_text(
                json.dumps(
                    {
                        "gate_id": "unit",
                        "gate_name": "Unit Tests",
                        "gradle_task": "testDebugUnitTest",
                        "merge_policy": "blocking",
                        "retry_enabled": True,
                        "attempts_used": 1,
                        "max_attempts": 2,
                        "status": "success",
                        "duration_seconds": 25,
                    }
                ),
                encoding="utf-8",
            )
            (artifacts_dir / "quality-assemble" / "gate-assemble.json").write_text(
                json.dumps(
                    {
                        "gate_id": "assemble",
                        "gate_name": "Assemble Debug",
                        "gradle_task": "assembleDebug",
                        "merge_policy": "blocking",
                        "retry_enabled": False,
                        "attempts_used": 1,
                        "max_attempts": 1,
                        "status": "success",
                        "duration_seconds": 11,
                    }
                ),
                encoding="utf-8",
            )

            results = load_results(artifacts_dir)

            self.assertEqual(["Assemble Debug", "Unit Tests"], [item.gate_name for item in results])

    def test_build_report_contains_table_and_policy(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            matrix_doc = Path(temp_dir) / "ci-quality-matrix.md"
            matrix_doc.write_text(
                "\n".join(
                    [
                        "## Merge policy",
                        "- Blocking: all quality gates.",
                        "- Warning-only: none.",
                    ]
                ),
                encoding="utf-8",
            )

            artifacts_dir = Path(temp_dir) / "artifacts"
            artifacts_dir.mkdir(parents=True)
            (artifacts_dir / "gate-quality.json").write_text(
                json.dumps(
                    {
                        "gate_id": "quality",
                        "gate_name": "Quality (ktlint + lint)",
                        "gradle_task": "qualityCheck",
                        "merge_policy": "blocking",
                        "retry_enabled": False,
                        "attempts_used": 1,
                        "max_attempts": 1,
                        "status": "success",
                        "duration_seconds": 42,
                    }
                ),
                encoding="utf-8",
            )

            report = build_report(load_results(artifacts_dir), matrix_doc)

            self.assertIn("## CI quality gates", report)
            self.assertIn("| Gate | Gradle task | Policy | Retry | Result | Duration (s) |", report)
            self.assertIn("Quality (ktlint + lint)", report)
            self.assertIn("PASS", report)
            self.assertIn("### Merge policy", report)
            self.assertIn("Blocking: all quality gates.", report)


if __name__ == "__main__":
    unittest.main()
