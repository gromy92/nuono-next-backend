package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.schedule.ScheduleSourcePage;
import com.nuono.next.datapull.schedule.ScheduleSourceReadContext;
import com.nuono.next.datapull.schedule.ScheduleSourceScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Aggregates one bounded native page into restart-safe member-set scan transitions. */
final class Dp08MemberSetScanCoordinator {
    private static final int READ_LIMIT = Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE + 1;

    private final Dp08MemberSetScheduleStore memberSets;

    Dp08MemberSetScanCoordinator(Dp08MemberSetScheduleStore memberSets) {
        this.memberSets = Objects.requireNonNull(memberSets, "memberSets");
    }

    int readLimit() {
        return READ_LIMIT;
    }

    ScheduleSourcePage resumeFinalization(
            ScheduleSourceReadContext context,
            Dp08MemberSourceCursor after,
            Dp08MemberStageHead head
    ) {
        Dp08MemberSetScheduleResult result = memberSets.resumeFinalization(
                context,
                head,
                after.logicalCursor()
        );
        return page(context, result, true);
    }

    ScheduleSourcePage scan(
            ScheduleSourceReadContext context,
            Dp08MemberSourceCursor after,
            Dp08MemberStageHead head,
            List<Dp08ScheduleMember> fetched
    ) {
        if (fetched.size() > READ_LIMIT) {
            throw new IllegalStateException("DP08 source exceeded SQL bound");
        }
        if (head != null && "SCANNING".equals(head.getStageState())) {
            return continueScope(context, after, head, fetched);
        }
        if (fetched.isEmpty()) {
            return new ScheduleSourcePage(List.of(), false, context.getLimit());
        }

        int consumed = Math.min(
                Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE,
                fetched.size()
        );
        List<ScheduleSourceScope> completed = new ArrayList<>();
        int start = 0;
        while (start < consumed) {
            Dp08ScheduleMember first = fetched.get(start);
            int end = scopeEnd(fetched, start, consumed, first.cursor());
            List<Dp08ScheduleMember> scopeMembers = fetched.subList(start, end);
            Dp08MemberSetBase base = requireBase(first);
            List<Dp08MemberSetItem> items = validatedItems(
                    first.scopeIdentity(),
                    scopeMembers
            );
            String nextCursor = fetched.get(end - 1).cursor().encode();
            boolean boundary = end < consumed || fetched.size() > consumed
                    && !first.cursor().sameScope(fetched.get(consumed).cursor());
            if (boundary || fetched.size() <= consumed) {
                Dp08MemberSetScheduleResult result = memberSets.completeSmall(
                        context,
                        base,
                        first.cursor().logicalCursor(),
                        nextCursor,
                        items
                );
                completed.add(result.scope());
            } else {
                memberSets.append(
                        context,
                        base,
                        first.cursor().logicalCursor(),
                        nextCursor,
                        items,
                        false
                );
            }
            start = end;
        }
        String cursor = fetched.get(consumed - 1).cursor().encode();
        return ScheduleSourcePage.scanned(
                completed,
                fetched.size() > consumed,
                context.getLimit(),
                cursor
        );
    }

    private ScheduleSourcePage continueScope(
            ScheduleSourceReadContext context,
            Dp08MemberSourceCursor after,
            Dp08MemberStageHead head,
            List<Dp08ScheduleMember> fetched
    ) {
        if (fetched.isEmpty() || !after.sameScope(fetched.get(0).cursor())) {
            Dp08MemberSetScheduleResult result = memberSets.finishExisting(
                    context,
                    head,
                    after.logicalCursor()
            );
            return page(context, result, true);
        }
        int end = 0;
        while (end < fetched.size()
                && end < Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE
                && after.sameScope(fetched.get(end).cursor())) {
            end++;
        }
        boolean complete = end < fetched.size() || fetched.size() < READ_LIMIT;
        List<Dp08MemberSetItem> items = validatedItems(
                Dp08ScheduleMember.scopeIdentity(memberSets.base(head)),
                fetched.subList(0, end)
        );
        String cursor = fetched.get(end - 1).cursor().encode();
        Dp08MemberSetScheduleResult result = memberSets.append(
                context,
                memberSets.base(head),
                after.logicalCursor(),
                cursor,
                items,
                complete
        );
        return page(context, result, true);
    }

    private int scopeEnd(
            List<Dp08ScheduleMember> fetched,
            int start,
            int consumed,
            Dp08MemberSourceCursor first
    ) {
        int end = start + 1;
        while (end < consumed && first.sameScope(fetched.get(end).cursor())) {
            end++;
        }
        return end;
    }

    private Dp08MemberSetBase requireBase(Dp08ScheduleMember first) {
        return Objects.requireNonNull(first.base(), "DP08 scope base member is missing");
    }

    private List<Dp08MemberSetItem> validatedItems(
            String expectedIdentity,
            List<Dp08ScheduleMember> members
    ) {
        List<Dp08MemberSetItem> result = new ArrayList<>(members.size());
        Set<String> keys = new HashSet<>();
        for (Dp08ScheduleMember member : members) {
            if (!expectedIdentity.equals(member.scopeIdentity())) {
                throw new IllegalStateException("DP08 grouped scope identity drift");
            }
            if (!keys.add(member.item().getMemberKey())) {
                throw new IllegalStateException("DP08 duplicate member identity");
            }
            result.add(member.item());
        }
        return result;
    }

    private ScheduleSourcePage page(
            ScheduleSourceReadContext context,
            Dp08MemberSetScheduleResult result,
            boolean more
    ) {
        return result.isComplete()
                ? ScheduleSourcePage.scanned(
                        List.of(result.scope()),
                        more,
                        context.getLimit(),
                        result.cursor()
                )
                : ScheduleSourcePage.progress(result.cursor(), context.getLimit());
    }
}
