#!/usr/bin/env python3
"""Fail-closed LEGACY environment filtering for governed backend cutovers."""
from __future__ import annotations


def build_legacy_env_contract_shell() -> str:
    return r'''
legacy_env_mode() {
  python3 - "$1" <<'PY'
import re, sys
values = []
for raw in open(sys.argv[1], encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    line = re.sub(r"^export\s+", "", line)
    key, value = line.split("=", 1)
    if key.strip() != "NUONO_DATA_PULL_EXECUTION_MODE":
        continue
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and ord(value[0]) in (34, 39):
        value = value[1:-1]
    values.append(value)
if not values:
    print("LEGACY_DEFAULT", end="")
elif values == ["LEGACY"]:
    print("LEGACY", end="")
else:
    raise SystemExit("runtime env is not unambiguously LEGACY")
PY
}
legacy_process_mode() {
  python3 - "$1" <<'PY'
import pathlib, sys
pid = sys.argv[1]
if not pid.isdecimal() or int(pid) <= 1:
    raise SystemExit("invalid runtime pid")
values = [item.split(b"=", 1)[1].decode() for item in
          pathlib.Path(f"/proc/{pid}/environ").read_bytes().split(b"\0")
          if item.startswith(b"NUONO_DATA_PULL_EXECUTION_MODE=")]
if not values:
    print("LEGACY_DEFAULT", end="")
elif values == ["LEGACY"]:
    print("LEGACY", end="")
else:
    raise SystemExit("runtime process is not unambiguously LEGACY")
PY
}
legacy_env_contract() {
  python3 - "$1" "$2" <<'PY' || return 1
import re, sys
path, policy = sys.argv[1:]
allowed = {
    "NUONO_DP10_OPEN_API_PROBE_CANARY_OWNER_USER_ID",
    "NUONO_DP10_OPEN_API_PROBE_CANARY_PROVIDER_ACCOUNT_ID",
}
counts = {key: 0 for key in allowed}
pattern = re.compile(r"^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=")
for raw in open(path, encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    match = pattern.match(line)
    if not match:
        continue
    key = match.group(1)
    if key in allowed:
        counts[key] += 1
        if policy != "source":
            raise SystemExit("LEGACY target contains a DP10 canary")
    elif (key.startswith("NUONO_DP10_") or key.startswith("NUONO_DP_RUNTIME_")
          or key in {"NUONO_MANAGED_DP_RELEASE", "NUONO_DATA_PULL_RUNTIME_ENABLED"}):
        raise SystemExit("LEGACY environment contains a runtime-only key")
if policy not in {"source", "target"}:
    raise SystemExit("LEGACY environment policy invalid")
if policy == "source" and sorted(counts.values()) not in ([0, 0], [1, 1]):
    raise SystemExit("LEGACY source DP10 canary pair is partial or duplicated")
PY
  [ "$(legacy_env_mode "$1")" = "$EXPECTED_DP_EXECUTION_MODE" ]
}
assert_legacy_source_env_contract() { legacy_env_contract "$1" source; }
assert_legacy_target_env_contract() { legacy_env_contract "$1" target; }
prepare_legacy_base_env() {
  local result=""
  result="$(python3 - "$1" "$2" "$3" <<'PY'
import hashlib, os, pathlib, re, stat, sys
source = pathlib.Path(sys.argv[1])
expected_sha = sys.argv[2]
target = pathlib.Path(sys.argv[3])
read_flags = os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW
write_flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_CLOEXEC | os.O_NOFOLLOW
allowed = {
    "NUONO_DP10_OPEN_API_PROBE_CANARY_OWNER_USER_ID",
    "NUONO_DP10_OPEN_API_PROBE_CANARY_PROVIDER_ACCOUNT_ID",
}
pattern = re.compile(r"^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=")
descriptor = os.open(source, read_flags)
try:
    metadata = os.fstat(descriptor)
    if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.geteuid()
            or metadata.st_nlink != 1 or stat.S_IMODE(metadata.st_mode) != 0o600):
        raise SystemExit("LEGACY source environment trust mismatch")
    raw = b""
    while True:
        chunk = os.read(descriptor, 1024 * 1024)
        if not chunk:
            break
        raw += chunk
finally:
    os.close(descriptor)
if hashlib.sha256(raw).hexdigest() != expected_sha:
    raise SystemExit("LEGACY source environment checksum changed")
text = raw.decode("utf-8")
counts = {key: 0 for key in allowed}
kept = []
for line in text.splitlines(keepends=True):
    match = pattern.match(line.strip())
    key = match.group(1) if match else None
    if key in allowed:
        counts[key] += 1
        continue
    kept.append(line)
if sorted(counts.values()) not in ([0, 0], [1, 1]):
    raise SystemExit("LEGACY source DP10 canary pair is partial or duplicated")
parent = target.parent
parent_metadata = parent.stat()
if (not stat.S_ISDIR(parent_metadata.st_mode) or parent_metadata.st_uid != os.geteuid()
        or stat.S_IMODE(parent_metadata.st_mode) & 0o022):
    raise SystemExit("LEGACY filtered environment parent is untrusted")
payload = "".join(kept).encode("utf-8")
output = os.open(target, write_flags, 0o600)
try:
    os.fchmod(output, 0o600)
    view = memoryview(payload)
    while view:
        written = os.write(output, view)
        if written <= 0:
            raise SystemExit("LEGACY filtered environment write failed")
        view = view[written:]
    os.fsync(output)
finally:
    os.close(output)
digest = hashlib.sha256(payload).hexdigest()
disposition = "STRIPPED_EXACT_DP10_CANARY_PAIR" if counts[next(iter(allowed))] else "NO_DP10_CANARY"
print(digest, disposition)
PY
)"
  LEGACY_BASE_ENV_SHA256="${result%% *}"
  LEGACY_CANARY_DISPOSITION="${result#* }"
  [[ "$LEGACY_BASE_ENV_SHA256" =~ ^[0-9a-f]{64}$ ]]
  [ "$LEGACY_CANARY_DISPOSITION" = NO_DP10_CANARY ] ||
    [ "$LEGACY_CANARY_DISPOSITION" = STRIPPED_EXACT_DP10_CANARY_PAIR ]
}
'''


__all__ = ["build_legacy_env_contract_shell"]
