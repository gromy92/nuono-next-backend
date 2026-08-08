from __future__ import annotations

import hashlib
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
ENTRYPOINT = "scripts/noon_auth_owner_scope_release.py"


def _module():
    spec = importlib.util.spec_from_file_location("scope_release_package", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def _env():
    return {
        "GITHUB_SHA": "b" * 40, "GITHUB_EVENT_NAME": "push",
        "GITHUB_REF": "refs/heads/master", "GITHUB_REPOSITORY": "gromy92/backend",
        "GITHUB_WORKFLOW": "Backend CI", "GITHUB_RUN_ID": "456",
        "GITHUB_RUN_ATTEMPT": "1",
    }


class NoonAuthOwnerScopeReleaseArtifactTest(unittest.TestCase):
    def test_packages_and_self_verifies_exact_release_bundle(self):
        module = _module()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "target"
            target.mkdir()
            (target / "nuono-next-backend-0.0.1.jar").write_bytes(b"jar")
            manifest = module.package_release_artifact(
                target, root / "out", "backend-" + "b" * 40, _env(),
                ROOT / "src/main/resources/db/init", ROOT,
            )
            value = json.loads(manifest.read_text(encoding="utf-8"))
            bundle = next(item for item in value["operation_bundles"]
                          if item["name"] == "noon_auth_owner_scope_release")
            self.assertEqual(ENTRYPOINT, bundle["entrypoint"])
            self.assertEqual(7, len(bundle["files"]))
            manifest_sha = hashlib.sha256(manifest.read_bytes()).hexdigest()
            command = (
                "import json;from noon_auth_owner_scope_release_artifact import "
                "load_release_provenance as load;"
                f"print(json.dumps(load(r'{manifest}',r'{manifest_sha}'),sort_keys=True))"
            )
            result = subprocess.run(
                [sys.executable, "-c", command], cwd=manifest.parent,
                env={**os.environ, "PYTHONPATH": "scripts", "PYTHONDONTWRITEBYTECODE": "1"},
                text=True, capture_output=True, check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(bundle["sha256"],
                             json.loads(result.stdout)["operationBundleSha256"])
            runnable = subprocess.run(
                [sys.executable, ENTRYPOINT, "--help"], cwd=manifest.parent,
                env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
                text=True, capture_output=True, check=False,
            )
            self.assertEqual(0, runnable.returncode, runnable.stderr)
            self.assertIn("release-manifest-sha256", runnable.stdout)


if __name__ == "__main__":
    unittest.main()
