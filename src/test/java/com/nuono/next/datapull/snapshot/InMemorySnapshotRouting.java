package com.nuono.next.datapull.snapshot;

/** Test-only routing decision for an accepted snapshot page. */
final class InMemorySnapshotRouting {
    final Integer nextPage;
    final String rejectionCode;

    private InMemorySnapshotRouting(Integer nextPage, String rejectionCode) {
        this.nextPage = nextPage;
        this.rejectionCode = rejectionCode;
    }

    static InMemorySnapshotRouting next(int pageNo) {
        return new InMemorySnapshotRouting(pageNo, null);
    }

    static InMemorySnapshotRouting last() {
        return new InMemorySnapshotRouting(null, null);
    }

    static InMemorySnapshotRouting reject(String code) {
        return new InMemorySnapshotRouting(null, code);
    }
}
