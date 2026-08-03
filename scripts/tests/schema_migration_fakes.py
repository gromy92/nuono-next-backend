from schema_migrations.core import MigrationState


class FakeDatabase:
    def __init__(self, states=None):
        self.states = dict(states or {})
        self.events = []
        self.postcheck_passes = True
        self.postcheck_results = {}
        self.livecheck_results = {}
        self.script_error = None

    def acquire_lock(self, timeout_seconds):
        self.events.append(("lock", timeout_seconds))

    def release_lock(self):
        self.events.append(("unlock",))

    def bootstrap(self, migration, release_commit, installed_by):
        self.events.append(("bootstrap", migration.key))
        self.states.setdefault(
            migration.key,
            MigrationState(
                migration.key,
                migration.checksum,
                migration.postcheck_checksum,
                "APPLIED",
                1,
            ),
        )

    def load_states(self):
        return dict(self.states)

    def begin(self, migration, release_commit, installed_by, operation):
        attempt = self.states.get(
            migration.key,
            MigrationState(
                migration.key,
                migration.checksum,
                migration.postcheck_checksum,
                "PENDING",
                0,
            ),
        ).attempt_no + 1
        self.states[migration.key] = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLYING",
            attempt,
        )
        self.events.append(("begin", migration.key, attempt, operation))
        return attempt

    def run_script(self, migration):
        self.events.append(("script", migration.key))
        if self.script_error:
            raise self.script_error

    def postcheck(self, migration):
        self.events.append(("postcheck", migration.key))
        return self.postcheck_results.get(
            migration.key,
            self.postcheck_passes,
        )

    def livecheck(self, migration):
        self.events.append(("livecheck", migration.key))
        return self.livecheck_results.get(
            migration.key,
            self.postcheck_passes,
        )

    def mark_applied(self, migration, attempt_no):
        self.states[migration.key] = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLIED",
            attempt_no,
        )
        self.events.append(("applied", migration.key, attempt_no))

    def mark_failed(self, migration, attempt_no, error):
        self.states[migration.key] = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "FAILED",
            attempt_no,
        )
        self.events.append(("failed", migration.key, attempt_no))

    def reconcile(
        self,
        migration,
        blocked_attempt_no,
        release_commit,
        installed_by,
    ):
        attempt_no = blocked_attempt_no + 1
        self.states[migration.key] = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLIED",
            attempt_no,
        )
        self.events.append(
            ("reconciled", migration.key, blocked_attempt_no, attempt_no)
        )
        return attempt_no
