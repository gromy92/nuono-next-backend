from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("package_release_artifact.py")


def load_module():
    spec = importlib.util.spec_from_file_location("package_release_artifact", MODULE_PATH)
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


class PackageReleaseArtifactTest(unittest.TestCase):
    def test_manifest_binds_managed_migration_hashes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            artifact = root / "nuono-next-backend.jar"
            artifact.write_bytes(b"jar")
            migration_dir = root / "db" / "init"
            migration_dir.mkdir(parents=True)
            names = [
                "182_product_barcode_psku_identity.sql",
                "189_product_barcode_store_identity_repair.sql",
                "190_noon_shared_email_auth_recovery.sql",
                "204_product_listing_workflow_attempt_claim.sql",
                "205_product_listing_reauthentication_attempt.sql",
                "206_product_barcode_store_uniqueness.sql",
            ]
            for name in names:
                (migration_dir / name).write_text(f"-- {name}\n", encoding="utf-8")
            forward = [
                ("227_database_migration_history.sql", "BOOTSTRAP"),
                ("228_noon_pull_runtime_schema_convergence.sql", "AUTO_ADDITIVE"),
            ]
            postcheck_dir = root / "db" / "postcheck"
            postcheck_dir.mkdir()
            catalog_lines = [
                "order\tmigration_key\tkind\tscript_path\tpostcheck_path\t"
                "livecheck_path"
            ]
            for order, (name, kind) in enumerate(forward, start=227):
                (migration_dir / name).write_text(f"-- {name}\n", encoding="utf-8")
                (postcheck_dir / name).write_text("SELECT 1;\n", encoding="utf-8")
                catalog_lines.append(
                    f"{order}\t{name}\t{kind}\tdb/init/{name}\t"
                    f"db/postcheck/{name}\tdb/postcheck/{name}"
                )
            (migration_dir / "release-migrations.tsv").write_text(
                "\n".join(catalog_lines) + "\n",
                encoding="utf-8",
            )

            manifest = module.build_manifest(
                artifact,
                "nuono-next-backend-" + "a" * 40,
                github_env(),
                migration_dir,
            )

            self.assertEqual(names, [item["path"] for item in manifest["migrations"]])
            self.assertEqual(
                [module.sha256_file(migration_dir / name) for name in names],
                [item["sha256"] for item in manifest["migrations"]],
            )
            self.assertEqual(
                [name for name, _ in forward],
                [item["migration_key"] for item in manifest["forward_migrations"]],
            )
            self.assertEqual(
                [kind for _, kind in forward],
                [item["kind"] for item in manifest["forward_migrations"]],
            )
            self.assertEqual(
                [
                    module.sha256_file(migration_dir / name)
                    for name, _ in forward
                ],
                [
                    item["script_sha256"]
                    for item in manifest["forward_migrations"]
                ],
            )
            self.assertEqual(
                [
                    module.sha256_file(postcheck_dir / name)
                    for name, _ in forward
                ],
                [
                    item["postcheck_sha256"]
                    for item in manifest["forward_migrations"]
                ],
            )
            self.assertEqual(
                [
                    module.sha256_file(postcheck_dir / name)
                    for name, _ in forward
                ],
                [
                    item["livecheck_sha256"]
                    for item in manifest["forward_migrations"]
                ],
            )

    def test_packages_one_jar_and_binds_it_to_the_workflow(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = root / "target"
            target.mkdir()
            (target / "nuono-next-backend-0.0.1-SNAPSHOT.jar").write_bytes(b"jar")
            manifest_path = module.package_release_artifact(
                target,
                root / "out",
                "nuono-next-backend-" + "a" * 40,
                github_env(),
            )
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual(manifest["commit"], "a" * 40)
            self.assertEqual(manifest["run_id"], 123)
            self.assertTrue(manifest["deployable"])
            self.assertEqual(manifest["files"][0]["path"], "nuono-next-backend.jar")
            self.assertEqual(
                manifest["files"][0]["sha256"],
                module.sha256_file(root / "out" / "nuono-next-backend.jar"),
            )

    def test_rejects_non_master_artifact(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            artifact = Path(tmp) / "nuono-next-backend.jar"
            artifact.write_bytes(b"jar")
            env = github_env()
            env["GITHUB_EVENT_NAME"] = "pull_request"
            with self.assertRaisesRegex(module.ArtifactError, "push to master"):
                module.build_manifest(artifact, "artifact", env)

    def test_rejects_ambiguous_jar(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp)
            (target / "nuono-next-backend-a.jar").write_bytes(b"a")
            (target / "nuono-next-backend-b.jar").write_bytes(b"b")
            with self.assertRaisesRegex(module.ArtifactError, "exactly one"):
                module.select_runnable_jar(target)


if __name__ == "__main__":
    unittest.main()
