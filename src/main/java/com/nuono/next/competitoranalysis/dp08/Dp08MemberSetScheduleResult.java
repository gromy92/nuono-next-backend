package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.schedule.ScheduleSourceScope;

/** One persistent nested-scan transition: progress-only or one completed scope. */
final class Dp08MemberSetScheduleResult {
    private final ScheduleSourceScope scope;
    private final String cursor;

    private Dp08MemberSetScheduleResult(ScheduleSourceScope scope, String cursor) {
        this.scope = scope;
        this.cursor = cursor;
    }

    static Dp08MemberSetScheduleResult progress(String cursor) {
        return new Dp08MemberSetScheduleResult(null, cursor);
    }

    static Dp08MemberSetScheduleResult complete(
            ScheduleSourceScope scope,
            String cursor
    ) {
        return new Dp08MemberSetScheduleResult(scope, cursor);
    }

    boolean isComplete() {
        return scope != null;
    }

    ScheduleSourceScope scope() {
        return scope;
    }

    String cursor() {
        return cursor;
    }
}
