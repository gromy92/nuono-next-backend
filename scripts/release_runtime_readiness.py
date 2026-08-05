#!/usr/bin/env python3
"""Generate the managed DP runtime readiness probe used during cutover."""
from __future__ import annotations


def build_runtime_readiness_shell() -> str:
    return r'''dp_runtime_health_status() {
  local body="" status=""
  body="$(curl -fsS --max-time 5 -- \
    "http://127.0.0.1:$TARGET_PORT/actuator/health/dpRuntime" 2>/dev/null)" || {
      printf UNAVAILABLE; return 0;
    }
  status="$(python3 - "$body" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except (json.JSONDecodeError, TypeError):
    raise SystemExit(1)
if not isinstance(data, dict) or data.get("status") != "UP":
    raise SystemExit(1)
print("UP", end="")
PY
)" || { printf UNAVAILABLE; return 0; }
  printf '%s' "$status"
}
assert_target_release_ready() {
  [ "$(health_status "$TARGET_PORT")" = UP ] &&
    assert_target_runtime_identity &&
    [ "$(dp_runtime_health_status)" = UP ]
}
'''


__all__ = ["build_runtime_readiness_shell"]
