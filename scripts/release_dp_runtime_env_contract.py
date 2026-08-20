#!/usr/bin/env python3
"""Build one unambiguous slot environment from canonical production config."""
from __future__ import annotations


def build_dp_runtime_env_contract_shell() -> str:
    return r'''
DP_RUNTIME_BASE_ENV_FILE=""
DP_RUNTIME_BASE_ENV_SHA256=""

prepare_dp_runtime_base_env() {
  local result=""
  result="$(python3 - "$1" "$2" "$3" <<'PY'
import hashlib, os, pathlib, re, stat, sys
source = pathlib.Path(sys.argv[1])
expected_sha = sys.argv[2]
target = pathlib.Path(sys.argv[3])
generated = {
    "NUONO_NEXT_APP_DIR", "NUONO_NEXT_PORT", "NUONO_NEXT_JAR",
    "NUONO_NEXT_LOG_FILE", "NUONO_NEXT_PID_FILE",
    "NUONO_NEXT_AUTH_SESSION_SECRET_FILE", "NUONO_MANAGED_DP_RELEASE",
    "NUONO_DATA_PULL_EXECUTION_MODE",
    "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE",
    "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256",
    "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT",
    "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE",
    "NUONO_DP_RUNTIME_RELEASE_EXPECTED_COMMIT",
    "NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256",
    "NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256",
    "NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE",
}
pattern = re.compile(r"^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=")
descriptor = os.open(source, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW)
try:
    metadata = os.fstat(descriptor)
    if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.geteuid()
            or metadata.st_nlink != 1 or stat.S_IMODE(metadata.st_mode) != 0o600):
        raise SystemExit("DP Runtime source environment trust mismatch")
    raw = b""
    while True:
        chunk = os.read(descriptor, 1024 * 1024)
        if not chunk:
            break
        raw += chunk
finally:
    os.close(descriptor)
if hashlib.sha256(raw).hexdigest() != expected_sha:
    raise SystemExit("DP Runtime source environment checksum changed")
seen = set()
removed = {}
kept = []
for line in raw.decode("utf-8").splitlines(keepends=True):
    match = pattern.match(line.strip())
    key = match.group(1) if match else None
    if key in generated:
        removed[key] = removed.get(key, 0) + 1
        if removed[key] > 1:
            raise SystemExit("DP Runtime source environment contains a duplicate generated key")
        continue
    if key is not None and key in seen:
        raise SystemExit("DP Runtime source environment contains a duplicate key")
    if key is not None:
        seen.add(key)
    kept.append(line)
parent = target.parent
metadata = parent.stat()
if (not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != os.geteuid()
        or stat.S_IMODE(metadata.st_mode) & 0o022):
    raise SystemExit("DP Runtime base environment parent is untrusted")
payload = "".join(kept).encode("utf-8")
output = os.open(
    target,
    os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_CLOEXEC | os.O_NOFOLLOW,
    0o600,
)
try:
    os.fchmod(output, 0o600)
    view = memoryview(payload)
    while view:
        written = os.write(output, view)
        if written <= 0:
            raise SystemExit("DP Runtime base environment write failed")
        view = view[written:]
    os.fsync(output)
finally:
    os.close(output)
print(hashlib.sha256(payload).hexdigest(), len(removed))
PY
)"
  DP_RUNTIME_BASE_ENV_SHA256="${result%% *}"
  [[ "$DP_RUNTIME_BASE_ENV_SHA256" =~ ^[0-9a-f]{64}$ ]]
  [[ "${result#* }" =~ ^[0-9]+$ ]]
}
'''


__all__ = ["build_dp_runtime_env_contract_shell"]
