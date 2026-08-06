package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08EvidenceBatchEvaluator;
import com.nuono.next.competitoranalysis.dp08.Dp08EvidenceRequestRow;
import com.nuono.next.competitoranalysis.dp08.Dp08EvidenceResultRow;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetHandle;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetItem;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskMemberProgress;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Dp08ScheduleEvidenceMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fenced, restart-safe DP08B evidence scan; every query and commit covers at most 64 members. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Dp08EvidenceBatchTransaction implements Dp08EvidenceBatchEvaluator {
    private static final int LIMIT=64;
    private final Dp08MemberSetMapper members;
    private final Dp08ScheduleEvidenceMapper evidence;
    private final Dp08FactFence fence;

    public Dp08EvidenceBatchTransaction(Dp08MemberSetMapper members,
            Dp08ScheduleEvidenceMapper evidence,Dp08RuntimeMapper runtimeMapper){this.members=members;
        this.evidence=evidence;this.fence=new Dp08FactFence(runtimeMapper);}

    @Override
    @Transactional(timeout=DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Result evaluate(DataPullTask task,Dp08MemberSetHandle handle,LocalDate factDate){
        fence.require(task,OperationCode.DP08B);requireHandle(task,handle);
        members.insertTaskProgress(task.getId(),OperationCode.DP08B,handle.getMemberSetId());
        Dp08TaskMemberProgress progress=Objects.requireNonNull(members.lockTaskProgress(task.getId()),"DP08 progress");
        verify(progress,handle);
        if(Boolean.TRUE.equals(progress.getEvidenceComplete()))
            return new Result(true,Boolean.TRUE.equals(progress.getExactSearchRequired()));
        List<Dp08MemberSetItem> page=List.copyOf(members.listMemberItemsAfter(
                handle.getMemberSetId(),progress.getEvidenceCursor(),LIMIT+1));
        if(page.isEmpty()||page.size()>LIMIT+1)throw new IllegalStateException("DP08 evidence member page invalid");
        boolean more=page.size()>LIMIT;List<Dp08MemberSetItem> selected=page.subList(0,Math.min(page.size(),LIMIT));
        List<Dp08EvidenceRequestRow> requests=new ArrayList<>(selected.size());
        for(Dp08MemberSetItem item:selected)requests.add(new Dp08EvidenceRequestRow(
                handle.getStableScopeKey(),factDate,item.getWatchProductId(),
                item.getCompetitorProductId(),handle.getNoonProductCode()));
        Map<String,Dp08EvidenceResultRow> results=new HashMap<>();
        for(Dp08EvidenceResultRow row:List.copyOf(evidence.listEvidence(requests))){
            if(results.put(key(row.getWatchProductId(),row.getCompetitorProductId()),row)!=null)
                throw new IllegalStateException("DP08 evidence duplicate");}
        if(results.size()!=selected.size())throw new IllegalStateException("DP08 evidence cohort drift");
        boolean required=Boolean.TRUE.equals(progress.getExactSearchRequired());
        for(Dp08MemberSetItem item:selected){Dp08EvidenceResultRow row=results.get(key(item.getWatchProductId(),item.getCompetitorProductId()));
            required|=!Boolean.TRUE.equals(row.getRanked())||!Boolean.TRUE.equals(row.getCompleteTitles());}
        long count=Math.addExact(progress.getEvidenceMemberCount(),selected.size());
        if((more&&count>=handle.getMemberCount())||(!more&&count!=handle.getMemberCount()))
            throw new IllegalStateException("DP08 evidence count drift");
        requireOne(members.advanceTaskEvidence(task.getId(),progress.getVersion(),progress.getEvidenceCursor(),
                progress.getEvidenceMemberCount(),selected.get(selected.size()-1).getMemberKey(),count,required,!more));
        fence.requireStillLive(task);return new Result(!more,required);
    }

    private static void requireHandle(DataPullTask task,Dp08MemberSetHandle handle){if(handle.getOperationCode()!=OperationCode.DP08B
            ||!task.getScopeKey().equals(handle.getStableScopeKey()))throw new IllegalStateException("DP08B handle drift");}
    private static void verify(Dp08TaskMemberProgress p,Dp08MemberSetHandle h){if(p.getOperationCode()!=h.getOperationCode()
            ||!p.getMemberSetId().equals(h.getMemberSetId()))throw new IllegalStateException("DP08 task progress drift");}
    private static String key(long watch,Long competitor){return watch+":"+competitor;}
    private static void requireOne(int changed){if(changed!=1)throw new IllegalStateException("DP08 evidence CAS lost");}
}
