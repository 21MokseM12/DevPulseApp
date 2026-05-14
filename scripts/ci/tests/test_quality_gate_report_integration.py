import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class QualityGateReportIntegrationTest(unittest.TestCase):
    def test_cli_generates_markdown_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            artifacts_dir = root / "artifacts"
            matrix_doc = root / "docs" / "ci-quality-matrix.md"
            output = root / "ci-quality-summary.md"

            (artifacts_dir / "quality-contract").mkdir(parents=True)
            (artifacts_dir / "quality-contract" / "gate-contract.json").write_text(
                json.dumps(
                    {
                        "gate_id": "contract",
                        "gate_name": "Contract Check",
                        "gradle_task": "contractCheck",
                        "merge_policy": "blocking",
                        "retry_enabled": True,
                        "attempts_used": 2,
                        "max_attempts": 2,
                        "status": "success",
                        "duration_seconds": 95,
                    }
                ),
                encoding="utf-8",
            )
            matrix_doc.parent.mkdir(parents=True)
            matrix_doc.write_text(
                "\n".join(
                    [
                        "# CI Quality Matrix",
                        "",
                        "## Merge policy",
                        "- Blocking: quality, unit, contract, assemble.",
                        "- Warning-only: none.",
                    ]
                ),
                encoding="utf-8",
            )

            command = [
                sys.executable,
                "scripts/ci/quality_gate_report.py",
                "--artifacts-dir",
                str(artifacts_dir),
                "--matrix-doc",
                str(matrix_doc),
                "--output",
                str(output),
            ]
            subprocess.run(command, check=True, cwd="/Users/moksem/AndroidStudioProjects/DevPulseApp")

            summary = output.read_text(encoding="utf-8")
            self.assertIn("Contract Check", summary)
            self.assertIn("`contractCheck`", summary)
            self.assertIn("2/2", summary)
            self.assertIn("Gates passed: **1**", summary)
            self.assertIn("Blocking: quality, unit, contract, assemble.", summary)


if __name__ == "__main__":
    unittest.main()
