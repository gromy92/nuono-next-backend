#!/usr/bin/env python3
"""Generate descriptor-safe managed Nginx upstream operations."""
from __future__ import annotations


def build_nginx_upstream_shell() -> str:
    return r'''nginx_upstream_operation() {
  python3 - "$@" <<'PY'
import hashlib, os, pathlib, re, secrets, stat, sys

READ_FLAGS = os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW
WRITE_FLAGS = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_CLOEXEC | os.O_NOFOLLOW
DIRECTORY_FLAGS = os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW
ALLOWED_MODES = {0o600, 0o640, 0o644}
EXPECTED_UID = os.geteuid()
MARKER = b"NUONO_BLUE_GREEN_MANAGED"
UPSTREAM = re.compile(rb"127\.0\.0\.1:([0-9]+)")

def abort(message):
    raise SystemExit(message)

def stable(metadata):
    return (
        metadata.st_dev, metadata.st_ino, metadata.st_uid, metadata.st_nlink,
        stat.S_IMODE(metadata.st_mode), metadata.st_size,
        metadata.st_mtime_ns, metadata.st_ctime_ns,
    )

def validate_regular(metadata, modes, label):
    if not stat.S_ISREG(metadata.st_mode):
        abort(f"{label} is not regular")
    if metadata.st_uid != EXPECTED_UID:
        abort(f"{label} owner mismatch")
    if metadata.st_nlink != 1:
        abort(f"{label} link count mismatch")
    if stat.S_IMODE(metadata.st_mode) not in modes:
        abort(f"{label} mode mismatch")

def open_parent(path):
    if not path.is_absolute() or path.name in {"", ".", ".."}:
        abort("managed upstream path must be absolute")
    try:
        descriptor = os.open(path.parent, DIRECTORY_FLAGS)
    except OSError as error:
        abort(f"managed upstream parent open failed: {error}")
    metadata = os.fstat(descriptor)
    if (not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != EXPECTED_UID or
            stat.S_IMODE(metadata.st_mode) & 0o022):
        os.close(descriptor)
        abort("managed upstream parent trust mismatch")
    return descriptor

def open_regular_at(parent, name, modes, label):
    try:
        descriptor = os.open(name, READ_FLAGS, dir_fd=parent)
    except OSError as error:
        abort(f"{label} open failed: {error}")
    metadata = os.fstat(descriptor)
    try:
        validate_regular(metadata, modes, label)
    except BaseException:
        os.close(descriptor)
        raise
    return descriptor, metadata

def read_stable(descriptor, before, label):
    chunks = []
    while True:
        chunk = os.read(descriptor, 1024 * 1024)
        if not chunk:
            break
        chunks.append(chunk)
    after = os.fstat(descriptor)
    if stable(after) != stable(before):
        abort(f"{label} changed during read")
    return b"".join(chunks)

def inspect(payload, expected_digest, label):
    digest = hashlib.sha256(payload).hexdigest()
    if expected_digest != "-" and digest != expected_digest:
        abort(f"{label} digest drift")
    if payload.count(MARKER) != 1:
        abort(f"{label} marker mismatch")
    matches = UPSTREAM.findall(payload)
    if len(matches) != 1:
        abort(f"{label} loopback upstream mismatch")
    port = int(matches[0])
    if port < 1 or port > 65535:
        abort(f"{label} port invalid")
    return digest, port

def target_metadata(parent, name):
    try:
        return os.stat(name, dir_fd=parent, follow_symlinks=False)
    except FileNotFoundError:
        return None

def write_all(descriptor, payload):
    remaining = memoryview(payload)
    while remaining:
        written = os.write(descriptor, remaining)
        if written <= 0:
            abort("managed upstream write failed")
        remaining = remaining[written:]

def atomic_replace(parent, name, original, mode, payload, expected_port):
    current = target_metadata(parent, name)
    if current is None or stable(current) != stable(original):
        abort("managed upstream changed before replace")
    temporary = f".{name}.{secrets.token_hex(16)}.tmp"
    created = False
    try:
        descriptor = os.open(temporary, WRITE_FLAGS, mode, dir_fd=parent)
        created = True
        try:
            os.fchmod(descriptor, mode)
            write_all(descriptor, payload)
            os.fsync(descriptor)
            validate_regular(os.fstat(descriptor), {mode}, "upstream temporary")
        finally:
            os.close(descriptor)
        current = target_metadata(parent, name)
        if current is None or stable(current) != stable(original):
            abort("managed upstream changed during preparation")
        os.replace(temporary, name, src_dir_fd=parent, dst_dir_fd=parent)
        created = False
        os.fsync(parent)
        verified, metadata = open_regular_at(parent, name, {mode}, "installed upstream")
        try:
            installed = read_stable(verified, metadata, "installed upstream")
        finally:
            os.close(verified)
        digest, port = inspect(installed, "-", "installed upstream")
        if port != expected_port:
            abort("installed upstream port mismatch")
        return digest
    finally:
        if created:
            try:
                os.unlink(temporary, dir_fd=parent)
                os.fsync(parent)
            except FileNotFoundError:
                pass

def read_managed(path, expected_digest, label):
    parent = open_parent(path)
    try:
        descriptor, metadata = open_regular_at(parent, path.name, ALLOWED_MODES, label)
        try:
            payload = read_stable(descriptor, metadata, label)
        finally:
            os.close(descriptor)
        digest, port = inspect(payload, expected_digest, label)
        return parent, metadata, payload, digest, port
    except BaseException:
        os.close(parent)
        raise

operation, *arguments = sys.argv[1:]
if operation == "inspect":
    if len(arguments) != 2:
        abort("upstream inspect arguments invalid")
    path = pathlib.Path(arguments[0])
    parent, _, _, digest, port = read_managed(path, arguments[1], "managed upstream")
    os.close(parent)
    print(digest, port)
elif operation == "replace":
    if len(arguments) != 4:
        abort("upstream replace arguments invalid")
    path = pathlib.Path(arguments[0])
    expected_port, new_port = int(arguments[2]), int(arguments[3])
    parent, metadata, payload, _, current_port = read_managed(
        path, arguments[1], "managed upstream",
    )
    try:
        if current_port != expected_port or not 1 <= new_port <= 65535:
            abort("managed upstream port drift")
        updated, count = UPSTREAM.subn(f"127.0.0.1:{new_port}".encode(), payload)
        if count != 1:
            abort("managed upstream replacement mismatch")
        print(atomic_replace(
            parent, path.name, metadata, stat.S_IMODE(metadata.st_mode), updated, new_port,
        ))
    finally:
        os.close(parent)
elif operation == "restore":
    if len(arguments) != 4:
        abort("upstream restore arguments invalid")
    path, backup = pathlib.Path(arguments[0]), pathlib.Path(arguments[2])
    parent, metadata, _, _, _ = read_managed(path, arguments[1], "managed upstream")
    backup_parent, _, backup_payload, backup_digest, backup_port = read_managed(
        backup, arguments[3], "managed upstream backup",
    )
    try:
        print(atomic_replace(
            parent, path.name, metadata, stat.S_IMODE(metadata.st_mode),
            backup_payload, backup_port,
        ))
        if backup_digest != arguments[3]:
            abort("managed upstream backup digest mismatch")
    finally:
        os.close(backup_parent)
        os.close(parent)
else:
    abort("unknown managed upstream operation")
PY
}
bind_nginx_upstream() {
  local state="" bound_digest="" bound_port=""
  state="$(nginx_upstream_operation inspect "$NGINX_UPSTREAM_FILE" -)" || return 1
  read -r bound_digest bound_port <<< "$state"
  [ "$bound_port" = "$1" ] || return 1
  NGINX_UPSTREAM_SHA256="$bound_digest"
  NGINX_UPSTREAM_ORIGINAL_SHA256="$bound_digest"
}
current_upstream_port() {
  local state="" current_digest="" current_port=""
  state="$(nginx_upstream_operation inspect \
    "$NGINX_UPSTREAM_FILE" "$NGINX_UPSTREAM_SHA256")" || return 1
  read -r current_digest current_port <<< "$state"
  [ "$current_digest" = "$NGINX_UPSTREAM_SHA256" ] || return 1
  printf '%s' "$current_port"
}
write_upstream_port() {
  local state="" current_digest="" current_port="" updated_digest=""
  state="$(nginx_upstream_operation inspect \
    "$NGINX_UPSTREAM_FILE" "$NGINX_UPSTREAM_SHA256")" || return 1
  read -r current_digest current_port <<< "$state"
  [ "$current_digest" = "$NGINX_UPSTREAM_SHA256" ] || return 1
  [ "$current_port" != "$1" ] || return 0
  updated_digest="$(nginx_upstream_operation replace "$NGINX_UPSTREAM_FILE" \
    "$NGINX_UPSTREAM_SHA256" "$current_port" "$1")" || return 1
  NGINX_UPSTREAM_SHA256="$updated_digest"
}
backup_nginx_upstream() {
  local backup_digest=""
  backup_digest="$(secure_file_operation install "$NGINX_UPSTREAM_FILE" "$1" \
    "600,640,644" 600 600 "$NGINX_UPSTREAM_SHA256" create-new "")" || return 1
  [ "$backup_digest" = "$NGINX_UPSTREAM_SHA256" ] || return 1
  NGINX_UPSTREAM_BACKUP_SHA256="$backup_digest"
}
restore_nginx_upstream() {
  local restored_digest=""
  restored_digest="$(nginx_upstream_operation restore "$NGINX_UPSTREAM_FILE" \
    "$NGINX_UPSTREAM_SHA256" "$UPSTREAM_BACKUP" \
    "$NGINX_UPSTREAM_BACKUP_SHA256")" || return 1
  [ "$restored_digest" = "$NGINX_UPSTREAM_BACKUP_SHA256" ] || return 1
  NGINX_UPSTREAM_SHA256="$restored_digest"
}
'''


__all__ = ["build_nginx_upstream_shell"]
