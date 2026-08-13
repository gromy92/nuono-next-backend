from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = Path(__file__).with_name("package_release_artifact.py")
ENTRYPOINT = "scripts/competitor_business_date_correction.py"


def load_module():
    spec = importlib.util.spec_from_file_location(
        "competitor_package_release_artifact",
        MODULE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def github_env() -> dict[str, str]:
    return {
        "GITHUB_SHA": "a" * 40,
        "GITHUB_EVENT_NAME": "push",
        "GITHUB_REF": "refs/heads/master",
        "GITHUB_REPOSITORY": "gromy92/nuono-next-backend",
        "GITHUB_WORKFLOW": "Backend CI",
        "GITHUB_RUN_ID": "123",
        "GITHUB_RUN_ATTEMPT": "2",
    }


class CompetitorReleaseArtifactTest(unittest.TestCase):
    def test_correction_migrations_are_explicitly_managed(self):
        module = load_module()
        correction_migrations = [
            name
            for name in module.MANAGED_MIGRATIONS
            if name.startswith(("240_", "241_"))
        ]

        self.assertEqual(2, len(correction_migrations))
        self.assertEqual(
            ["240", "241"],
            [name.split("_", 1)[0] for name in correction_migrations],
        )
        for name in correction_migrations:
            with self.subTest(migration=name):
                self.assertTrue(
                    (ROOT / "src/main/resources/db/init" / name).is_file()
                )

    def test_packages_runnable_hash_bound_correction_bundle(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "target"
            target.mkdir()
            (target / "nuono-next-backend-0.0.1-SNAPSHOT.jar").write_bytes(
                b"jar"
            )

            manifest_path = module.package_release_artifact(
                target,
                root / "out",
                "nuono-next-backend-" + "a" * 40,
                github_env(),
                ROOT / "src/main/resources/db/init",
                ROOT,
            )
            output_root = manifest_path.parent
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            bundles = manifest["operation_bundles"]

            self.assertEqual(5, len(bundles))
            bundle = next(item for item in bundles if item["name"] == "competitor_business_date_correction")
            self.assertEqual(
                "competitor_business_date_correction",
                bundle["name"],
            )
            self.assertEqual(ENTRYPOINT, bundle["entrypoint"])
            identity = {
                key: bundle[key]
                for key in ("schema_version", "sha256", "files")
            }
            self.assertEqual(
                sorted(item["path"] for item in identity["files"]),
                [item["path"] for item in identity["files"]],
            )
            self.assertNotIn(
                "__pycache__",
                "\n".join(item["path"] for item in identity["files"]),
            )
            for item in identity["files"]:
                with self.subTest(path=item["path"]):
                    frozen = output_root / item["path"]
                    self.assertTrue(frozen.is_file())
                    self.assertFalse(frozen.is_symlink())
                    self.assertEqual(item["size"], frozen.stat().st_size)
                    self.assertEqual(
                        item["sha256"],
                        module.sha256_file(frozen),
                    )

            probe = subprocess.run(
                [
                    sys.executable,
                    "-c",
                    (
                        "import json,sys;"
                        "sys.path.insert(0,'scripts');"
                        "from competitor_business_date.bundle_identity "
                        "import operation_bundle_identity;"
                        "print(json.dumps(operation_bundle_identity(),sort_keys=True))"
                    ),
                ],
                cwd=output_root,
                env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual("", probe.stderr)
            self.assertEqual(0, probe.returncode)
            self.assertEqual(identity, json.loads(probe.stdout))

            runnable = subprocess.run(
                [sys.executable, ENTRYPOINT, "--help"],
                cwd=output_root,
                env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual("", runnable.stderr)
            self.assertEqual(0, runnable.returncode)
            self.assertIn("governed correction", runnable.stdout)


if __name__ == "__main__":
    unittest.main()
