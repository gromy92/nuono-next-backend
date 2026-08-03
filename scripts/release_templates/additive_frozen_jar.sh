freeze_staged_jar() {
  python3 - "$STAGED_JAR" "$FROZEN_JAR" "$EXPECTED_JAR_SHA256" <<'PY'
import hashlib
import os
import stat
import sys
from pathlib import Path

source_path = Path(sys.argv[1])
destination_path = Path(sys.argv[2])
expected_sha256 = sys.argv[3]
maximum_size = 512 * 1024 * 1024
digest = hashlib.sha256()
copied = 0
destination_created = False

try:
    source_fd = os.open(
        source_path,
        os.O_RDONLY
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
    )
    with os.fdopen(source_fd, "rb") as source:
        before = os.fstat(source.fileno())
        if not stat.S_ISREG(before.st_mode):
            raise SystemExit("staged Jar must be a regular non-symlink")
        if before.st_size < 1 or before.st_size > maximum_size:
            raise SystemExit("staged Jar size is outside the governed limit")
        target_fd = os.open(
            destination_path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL,
            0o400,
        )
        destination_created = True
        with os.fdopen(target_fd, "wb") as target:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                copied += len(chunk)
                if copied > maximum_size:
                    raise SystemExit("staged Jar exceeds the governed limit")
                digest.update(chunk)
                target.write(chunk)
            target.flush()
            os.fsync(target.fileno())
        after = os.fstat(source.fileno())
    if (
        copied != before.st_size
        or (before.st_dev, before.st_ino, before.st_size)
        != (after.st_dev, after.st_ino, after.st_size)
    ):
        raise SystemExit("staged Jar changed while it was being frozen")
    if digest.hexdigest() != expected_sha256:
        raise SystemExit("staged Jar checksum does not match governed cutover")
except BaseException:
    if destination_created:
        destination_path.unlink(missing_ok=True)
    raise
PY
}
