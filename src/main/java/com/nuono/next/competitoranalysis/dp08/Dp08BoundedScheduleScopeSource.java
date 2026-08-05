package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleScopeSource;
import com.nuono.next.datapull.schedule.ScheduleSourcePage;
import com.nuono.next.datapull.schedule.ScheduleSourceReadContext;
import com.nuono.next.infrastructure.mapper.Dp08BoundedScheduleScopeMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Reads one bounded native DP08 page and delegates persistent nested-scope assembly. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Dp08BoundedScheduleScopeSource implements ScheduleScopeSource {
    private final Dp08BoundedScheduleScopeMapper source;
    private final Dp08MemberSetScheduleStore memberSets;
    private final Dp08MemberSetScanCoordinator scanner;

    public Dp08BoundedScheduleScopeSource(
            Dp08BoundedScheduleScopeMapper source,
            Dp08MemberSetMapper memberSetMapper,
            ObjectMapper objectMapper
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.memberSets = new Dp08MemberSetScheduleStore(memberSetMapper, objectMapper);
        this.scanner = new Dp08MemberSetScanCoordinator(memberSets);
    }

    @Override
    public Set<OperationCode> operations() {
        return Set.of(OperationCode.DP08A, OperationCode.DP08B);
    }

    @Override
    public ScheduleSourcePage readPage(
            OperationCode operation,
            String afterNativeCursorExclusive,
            Instant untilInclusive,
            int limit
    ) {
        throw new IllegalStateException(
                "DP08 nested scans require a persistent epoch/pass context"
        );
    }

    @Override
    public ScheduleSourcePage readPage(ScheduleSourceReadContext context) {
        OperationCode operation = requireOperation(context.getOperationCode());
        Dp08MemberSourceCursor after = Dp08MemberSourceCursor.parse(
                operation,
                context.getAfterNativeCursorExclusive()
        );
        Dp08MemberStageHead head = after == null
                ? null
                : memberSets.lockHead(context, after.scopeKey());
        if (head != null && "FINALIZING".equals(head.getStageState())) {
            return scanner.resumeFinalization(context, after, head);
        }
        return operation == OperationCode.DP08A
                ? keywordPage(context, after, head)
                : listPage(context, after, head);
    }

    private ScheduleSourcePage keywordPage(
            ScheduleSourceReadContext context,
            Dp08MemberSourceCursor after,
            Dp08MemberStageHead head
    ) {
        List<Dp08KeywordScopeRow> rows = List.copyOf(source.listKeywordMembersAfter(
                after == null ? null : after.owner(),
                after == null ? null : after.watch(),
                after == null ? null : after.keyword(),
                after == null ? null : after.memberOrder(),
                after == null ? null : after.memberId(),
                scanner.readLimit()
        ));
        List<Dp08ScheduleMember> members = new ArrayList<>(rows.size());
        for (Dp08KeywordScopeRow row : rows) {
            members.add(Dp08ScheduleMember.from(row));
        }
        return scanner.scan(context, after, head, members);
    }

    private ScheduleSourcePage listPage(
            ScheduleSourceReadContext context,
            Dp08MemberSourceCursor after,
            Dp08MemberStageHead head
    ) {
        List<Dp08ListTargetRow> rows = List.copyOf(source.listTargetMembersAfter(
                after == null ? null : after.owner(),
                after == null ? null : after.store(),
                after == null ? null : after.site(),
                after == null ? null : after.noonCode(),
                after == null ? null : after.watch(),
                after == null ? null : after.memberId(),
                scanner.readLimit()
        ));
        List<Dp08ScheduleMember> members = new ArrayList<>(rows.size());
        for (Dp08ListTargetRow row : rows) {
            members.add(Dp08ScheduleMember.from(row));
        }
        return scanner.scan(context, after, head, members);
    }

    private OperationCode requireOperation(OperationCode operation) {
        if (operation != OperationCode.DP08A && operation != OperationCode.DP08B) {
            throw new IllegalArgumentException("DP08 source received another operation");
        }
        return operation;
    }
}
