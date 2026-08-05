#!/usr/bin/env python3
"""Descriptor-safe atomic file primitives embedded in governed release scripts."""
from __future__ import annotations

def build_secure_file_shell() -> str:
    return r'''
secure_file_operation() {
  python3 - "$@" <<'PY'
import hashlib, os, pathlib, secrets, stat, sys
READ_FLAGS = os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW
WRITE_FLAGS = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_CLOEXEC | os.O_NOFOLLOW
DIRECTORY_FLAGS = os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW
CHUNK_BYTES = 1024 * 1024
EXPECTED_UID = os.geteuid()
def abort(message):
    raise SystemExit(message)
def octal_modes(raw):
    try:
        modes = {int(value, 8) for value in raw.split(",") if value}
    except ValueError:
        abort("secure file mode policy invalid")
    if not modes:
        abort("secure file mode policy empty")
    return modes
def validate_regular(metadata, modes, label):
    mode = stat.S_IMODE(metadata.st_mode)
    if not stat.S_ISREG(metadata.st_mode):
        abort(f"{label} is not a regular file")
    if metadata.st_uid != EXPECTED_UID:
        abort(f"{label} owner mismatch")
    if metadata.st_nlink != 1:
        abort(f"{label} link count mismatch")
    if mode not in modes:
        abort(f"{label} mode mismatch: {mode:o}")
def open_regular(path, modes, label):
    if not path.is_absolute():
        abort(f"{label} path must be absolute")
    try:
        descriptor = os.open(path, READ_FLAGS)
    except OSError as error:
        abort(f"{label} open failed: {error}")
    try:
        metadata = os.fstat(descriptor)
        validate_regular(metadata, modes, label)
        return descriptor, metadata
    except BaseException:
        os.close(descriptor)
        raise
def stable_identity(metadata):
    return (
        metadata.st_dev, metadata.st_ino, metadata.st_uid, metadata.st_nlink,
        stat.S_IMODE(metadata.st_mode), metadata.st_size,
        metadata.st_mtime_ns, metadata.st_ctime_ns,
    )
def digest_descriptor(descriptor):
    digest = hashlib.sha256()
    while True:
        chunk = os.read(descriptor, CHUNK_BYTES)
        if not chunk:
            return digest.hexdigest()
        digest.update(chunk)
def require_expected(actual, expected, label):
    if expected != "-" and actual != expected:
        abort(f"{label} SHA-256 mismatch")
def open_secure_parent(path):
    if not path.is_absolute() or path.name in {"", ".", ".."}:
        abort("secure target path invalid")
    try:
        descriptor = os.open(path.parent, DIRECTORY_FLAGS)
    except OSError as error:
        abort(f"secure target directory open failed: {error}")
    metadata = os.fstat(descriptor)
    mode = stat.S_IMODE(metadata.st_mode)
    if (not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != EXPECTED_UID or
            mode & 0o022):
        os.close(descriptor)
        abort("secure target directory trust mismatch")
    return descriptor
def target_metadata(parent_descriptor, name):
    try:
        return os.stat(name, dir_fd=parent_descriptor, follow_symlinks=False)
    except FileNotFoundError:
        return None
def validate_target_precondition(metadata, modes, policy):
    if metadata is None:
        return
    if policy == "create-new":
        abort("secure target already exists")
    validate_regular(metadata, modes, "secure target")
def write_all(descriptor, payload):
    view = memoryview(payload)
    while view:
        written = os.write(descriptor, view)
        if written <= 0:
            abort("secure temporary write failed")
        view = view[written:]
def finalize_temporary(parent_descriptor, temporary, target, target_mode,
                       existing_modes, policy, initial_target, expected_digest):
    current_target = target_metadata(parent_descriptor, target)
    validate_target_precondition(current_target, existing_modes, policy)
    if initial_target is None:
        if current_target is not None:
            abort("secure target appeared during preparation")
    elif current_target is None or stable_identity(current_target) != stable_identity(initial_target):
        abort("secure target changed during preparation")
    if policy == "create-new":
        os.link(
            temporary, target,
            src_dir_fd=parent_descriptor, dst_dir_fd=parent_descriptor,
            follow_symlinks=False,
        )
        os.fsync(parent_descriptor)
        os.unlink(temporary, dir_fd=parent_descriptor)
        os.fsync(parent_descriptor)
    else:
        os.replace(
            temporary, target,
            src_dir_fd=parent_descriptor, dst_dir_fd=parent_descriptor,
        )
        os.fsync(parent_descriptor)
    descriptor = os.open(target, READ_FLAGS, dir_fd=parent_descriptor)
    try:
        metadata = os.fstat(descriptor)
        validate_regular(metadata, {target_mode}, "secure installed target")
        actual_digest = digest_descriptor(descriptor)
    finally:
        os.close(descriptor)
    if actual_digest != expected_digest:
        abort("secure installed target SHA-256 mismatch")
def install_file(arguments):
    if len(arguments) != 8:
        abort("secure install arguments invalid")
    source = pathlib.Path(arguments[0])
    target = pathlib.Path(arguments[1])
    source_modes = octal_modes(arguments[2])
    target_mode = int(arguments[3], 8)
    existing_modes = octal_modes(arguments[4])
    expected_source = arguments[5]
    policy = arguments[6]
    suffix = arguments[7].encode("utf-8")
    if policy not in {"replace", "create-new"}:
        abort("secure install policy invalid")
    source_descriptor, source_before = open_regular(source, source_modes, "secure source")
    parent_descriptor = open_secure_parent(target)
    initial_target = target_metadata(parent_descriptor, target.name)
    validate_target_precondition(initial_target, existing_modes, policy)
    temporary = f".{target.name}.{secrets.token_hex(16)}.tmp"
    temporary_created = False
    source_digest = hashlib.sha256()
    installed_digest = hashlib.sha256()
    try:
        temporary_descriptor = os.open(
            temporary, WRITE_FLAGS, target_mode, dir_fd=parent_descriptor,
        )
        temporary_created = True
        try:
            os.fchmod(temporary_descriptor, target_mode)
            while True:
                chunk = os.read(source_descriptor, CHUNK_BYTES)
                if not chunk:
                    break
                source_digest.update(chunk)
                installed_digest.update(chunk)
                write_all(temporary_descriptor, chunk)
            if suffix:
                installed_digest.update(suffix)
                write_all(temporary_descriptor, suffix)
            os.fsync(temporary_descriptor)
            validate_regular(os.fstat(temporary_descriptor), {target_mode}, "secure temporary")
        finally:
            os.close(temporary_descriptor)
        source_after = os.fstat(source_descriptor)
        if stable_identity(source_after) != stable_identity(source_before):
            abort("secure source changed during copy")
        require_expected(source_digest.hexdigest(), expected_source, "secure source")
        final_digest = installed_digest.hexdigest()
        finalize_temporary(
            parent_descriptor, temporary, target.name, target_mode,
            existing_modes, policy, initial_target, final_digest,
        )
        temporary_created = False
        print(final_digest)
    finally:
        os.close(source_descriptor)
        if temporary_created:
            try:
                os.unlink(temporary, dir_fd=parent_descriptor)
                os.fsync(parent_descriptor)
            except FileNotFoundError:
                pass
        os.close(parent_descriptor)
def write_file(arguments):
    if len(arguments) != 6:
        abort("secure write arguments invalid")
    target = pathlib.Path(arguments[0])
    target_mode = int(arguments[1], 8)
    existing_modes = octal_modes(arguments[2])
    policy = arguments[3]
    encoding = arguments[4]
    if encoding != "utf8":
        abort("secure write encoding invalid")
    payload = arguments[5].encode("utf-8")
    if policy not in {"replace", "create-new"}:
        abort("secure write policy invalid")
    parent_descriptor = open_secure_parent(target)
    initial_target = target_metadata(parent_descriptor, target.name)
    validate_target_precondition(initial_target, existing_modes, policy)
    temporary = f".{target.name}.{secrets.token_hex(16)}.tmp"
    temporary_created = False
    try:
        descriptor = os.open(temporary, WRITE_FLAGS, target_mode, dir_fd=parent_descriptor)
        temporary_created = True
        try:
            os.fchmod(descriptor, target_mode)
            write_all(descriptor, payload)
            os.fsync(descriptor)
            validate_regular(os.fstat(descriptor), {target_mode}, "secure temporary")
        finally:
            os.close(descriptor)
        digest = hashlib.sha256(payload).hexdigest()
        finalize_temporary(
            parent_descriptor, temporary, target.name, target_mode,
            existing_modes, policy, initial_target, digest,
        )
        temporary_created = False
        print(digest)
    finally:
        if temporary_created:
            try:
                os.unlink(temporary, dir_fd=parent_descriptor)
                os.fsync(parent_descriptor)
            except FileNotFoundError:
                pass
        os.close(parent_descriptor)
def prepare_directory(arguments):
    if len(arguments) != 4:
        abort("secure directory arguments invalid")
    path = pathlib.Path(arguments[0])
    allowed_modes = octal_modes(arguments[1])
    create_mode = int(arguments[2], 8)
    policy = arguments[3]
    if policy not in {"accept", "create-new"}:
        abort("secure directory policy invalid")
    parent_descriptor = open_secure_parent(path)
    try:
        metadata = target_metadata(parent_descriptor, path.name)
        if metadata is not None and policy == "create-new":
            abort("secure directory already exists")
        if metadata is None:
            os.mkdir(path.name, create_mode, dir_fd=parent_descriptor)
            os.fsync(parent_descriptor)
            metadata = target_metadata(parent_descriptor, path.name)
        if metadata is None or not stat.S_ISDIR(metadata.st_mode):
            abort("secure directory is not a directory")
        if metadata.st_uid != EXPECTED_UID or stat.S_IMODE(metadata.st_mode) not in allowed_modes:
            abort("secure directory trust mismatch")
        descriptor = os.open(path.name, DIRECTORY_FLAGS, dir_fd=parent_descriptor)
        try:
            verified = os.fstat(descriptor)
            if (not stat.S_ISDIR(verified.st_mode) or verified.st_uid != EXPECTED_UID or
                    stat.S_IMODE(verified.st_mode) not in allowed_modes or
                    (verified.st_dev, verified.st_ino) != (metadata.st_dev, metadata.st_ino)):
                abort("secure directory changed during verification")
        finally:
            os.close(descriptor)
    finally:
        os.close(parent_descriptor)
operation, *arguments = sys.argv[1:]
if operation == "verify":
    if len(arguments) != 3:
        abort("secure verify arguments invalid")
    descriptor, before = open_regular(
        pathlib.Path(arguments[0]), octal_modes(arguments[1]), "secure verified file",
    )
    try:
        digest = digest_descriptor(descriptor)
        after = os.fstat(descriptor)
    finally:
        os.close(descriptor)
    if stable_identity(after) != stable_identity(before):
        abort("secure verified file changed during read")
    require_expected(digest, arguments[2], "secure verified file")
    print(digest)
elif operation == "install":
    install_file(arguments)
elif operation == "write":
    write_file(arguments)
elif operation == "directory":
    prepare_directory(arguments)
else:
    abort("secure file operation invalid")
PY
}
'''

__all__ = ["build_secure_file_shell"]
