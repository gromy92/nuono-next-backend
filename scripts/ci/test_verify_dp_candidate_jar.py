from __future__ import annotations

import pathlib
import tempfile
import unittest
import zipfile

from scripts.ci import verify_dp_candidate_jar

ROOT = pathlib.Path(__file__).resolve().parents[2]


class VerifyDpCandidateJarTest(unittest.TestCase):
    def test_pom_places_managed_markers_in_the_executable_classpath_once(self):
        pom = (ROOT / "pom.xml").read_text(encoding="utf-8")

        self.assertIn("<exclude>META-INF/nuono/**</exclude>", pom)
        self.assertIn(
            "<directory>src/main/resources/META-INF/nuono</directory>", pom
        )
        self.assertIn(
            "<targetPath>BOOT-INF/classes/META-INF/nuono</targetPath>", pom
        )

    def test_accepts_candidate_with_all_markers_and_no_retired_surface(self):
        with tempfile.TemporaryDirectory() as directory:
            candidate = self.write_candidate(pathlib.Path(directory))

            digest = verify_dp_candidate_jar.verify(candidate)

            self.assertRegex(digest, r"^[0-9a-f]{64}$")

    def test_rejects_fake_provider_in_production_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            candidate = self.write_candidate(
                pathlib.Path(directory),
                "BOOT-INF/classes/com/nuono/next/procurement/aliorder/"
                "FakeAli1688HistoricalOrderProvider.class",
            )

            with self.assertRaisesRegex(
                ValueError, "DP_CANDIDATE_RETIRED_SURFACE_PRESENT"
            ):
                verify_dp_candidate_jar.verify(candidate)

    def test_rejects_retired_readiness_package(self):
        with tempfile.TemporaryDirectory() as directory:
            candidate = self.write_candidate(
                pathlib.Path(directory),
                "BOOT-INF/classes/com/nuono/next/noonreadiness/Retired.class",
            )

            with self.assertRaisesRegex(
                ValueError, "DP_CANDIDATE_RETIRED_SURFACE_PRESENT"
            ):
                verify_dp_candidate_jar.verify(candidate)

    def test_rejects_missing_release_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            candidate = root / "candidate.jar"
            with zipfile.ZipFile(candidate, "w") as archive:
                for entry in sorted(verify_dp_candidate_jar.REQUIRED_ENTRIES)[1:]:
                    archive.writestr(entry, "contract\n")

            with self.assertRaisesRegex(ValueError, "DP_CANDIDATE_MARKER_MISSING"):
                verify_dp_candidate_jar.verify(candidate)

    def test_rejects_root_level_duplicate_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            candidate = self.write_candidate(
                pathlib.Path(directory),
                "META-INF/nuono/dp-runtime-cutover-manifest-v1",
            )

            with self.assertRaisesRegex(
                ValueError, "DP_CANDIDATE_MARKER_OUTSIDE_CLASSPATH"
            ):
                verify_dp_candidate_jar.verify(candidate)

    def write_candidate(self, root: pathlib.Path, extra: str | None = None) -> pathlib.Path:
        candidate = root / "candidate.jar"
        with zipfile.ZipFile(candidate, "w") as archive:
            for entry in sorted(verify_dp_candidate_jar.REQUIRED_ENTRIES):
                archive.writestr(entry, "contract\n")
            if extra:
                archive.writestr(extra, b"retired")
        return candidate


if __name__ == "__main__":
    unittest.main()
