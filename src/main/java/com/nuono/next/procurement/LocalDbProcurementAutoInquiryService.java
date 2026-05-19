package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryEventView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.CandidateSnapshotView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.DemandSnapshotView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementAutoInquiryService {

    private final ProcurementMapper procurementMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final ProcurementStructuredFieldParser procurementStructuredFieldParser;
    private final ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor;
    private final ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor;
    private final ProcurementAutoInquiryEventLogger procurementAutoInquiryEventLogger;
    private final ProcurementAutoInquiryExecutor procurementAutoInquiryExecutor;

    public LocalDbProcurementAutoInquiryService(
            ProcurementMapper procurementMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService,
            ProcurementStructuredFieldParser procurementStructuredFieldParser,
            ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor,
            ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor,
            ProcurementAutoInquiryEventLogger procurementAutoInquiryEventLogger,
            ProcurementAutoInquiryExecutor procurementAutoInquiryExecutor
    ) {
        this.procurementMapper = procurementMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.procurementStructuredFieldParser = procurementStructuredFieldParser;
        this.procurementDecisionSupportAdvisor = procurementDecisionSupportAdvisor;
        this.procurementInquiryPreparationAdvisor = procurementInquiryPreparationAdvisor;
        this.procurementAutoInquiryEventLogger = procurementAutoInquiryEventLogger;
        this.procurementAutoInquiryExecutor = procurementAutoInquiryExecutor;
    }

    public ProcurementAutoInquiryWorkbenchView buildWorkbench(Long ownerUserId, Long demandItemId, Long candidateId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取自动询价工作台。");
        }
        if (demandItemId == null) {
            throw new IllegalArgumentException("请先选择一条采购需求，再查看自动询价工作台。");
        }

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
        view.setMode("local-db");
        view.setReady(inspection.isReady());
        if (!inspection.isReady()) {
            view.setMessage("本地数据库核心表还没有准备好，自动询价工作台暂时不能读取。");
            return view;
        }

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(ownerUserId, demandItemId);
        if (demandItem == null) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，暂时不能读取自动询价工作台。");
        }
        procurementStructuredFieldParser.enrichDemandItem(demandItem);
        view.setDemandItem(toDemandSnapshot(demandItem));

        Long resolvedCandidateId = resolveCandidateId(ownerUserId, demandItem, candidateId);
        CandidateView candidate = resolvedCandidateId == null
                ? null
                : procurementMapper.selectOwnedCandidate(ownerUserId, demandItemId, resolvedCandidateId);
        if (candidate != null) {
            procurementStructuredFieldParser.enrichCandidate(candidate);
            procurementDecisionSupportAdvisor.enrichCandidateDecisionSupport(demandItem, candidate);
            procurementInquiryPreparationAdvisor.enrichCandidateInquiryPreparation(demandItem, candidate);
            view.setCandidate(toCandidateSnapshot(candidate));
        }

        List<AutoInquiryTaskView> tasks = procurementMapper.listAutoInquiryTasks(ownerUserId, demandItemId, resolvedCandidateId);
        for (AutoInquiryTaskView task : tasks) {
            enrichTaskLabels(task);
        }
        if (!tasks.isEmpty()) {
            AutoInquiryTaskView latestTask = tasks.get(0);
            latestTask.setEvents(loadEvents(latestTask.getId()));
            view.setLatestTask(latestTask);
        }
        view.setTaskHistory(tasks);

        List<AutoInquirySessionView> sessions = procurementMapper.listAutoInquirySessions(
                ProcurementAutoInquiryLifecycle.PLATFORM_1688
        );
        for (AutoInquirySessionView session : sessions) {
            session.setStatusLabel(ProcurementAutoInquiryLifecycle.sessionStatusLabel(session.getStatus()));
        }
        view.setSessionPool(sessions);

        view.setReady(true);
        view.setMessage(resolveWorkbenchMessage(view, candidate));
        return view;
    }

    @Transactional
    public ProcurementAutoInquiryWorkbenchView startAutoInquiry(ProcurementAutoInquiryStartCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能创建自动询价任务。");
        }
        if (command.getDemandItemId() == null || command.getCandidateId() == null) {
            throw new IllegalArgumentException("请先明确要自动询价的需求和候选商品。");
        }

        Long ownerUserId = command.getOwnerUserId();
        Long operatorUserId = resolveOperatorUserId(command);

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(ownerUserId, command.getDemandItemId());
        if (demandItem == null) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，暂时不能发起自动询价。");
        }

        CandidateView candidate = procurementMapper.selectOwnedCandidate(
                ownerUserId,
                command.getDemandItemId(),
                command.getCandidateId()
        );
        if (candidate == null) {
            throw new IllegalArgumentException("当前候选商品不在老板名下的采购单里，暂时不能发起自动询价。");
        }

        AutoInquiryTaskView activeTask = procurementMapper.selectLatestActiveAutoInquiryTask(
                ownerUserId,
                command.getDemandItemId(),
                command.getCandidateId()
        );
        if (activeTask != null && ProcurementAutoInquiryLifecycle.isActiveStatus(activeTask.getStatus())) {
            if (!Objects.equals(Boolean.FALSE, command.getTriggerDispatch())) {
                procurementAutoInquiryExecutor.execute(activeTask.getId(), operatorUserId);
            }
            ProcurementAutoInquiryWorkbenchView existingView = buildWorkbench(
                    ownerUserId,
                    command.getDemandItemId(),
                    command.getCandidateId()
            );
            existingView.setMessage("当前候选已经有一条进行中的自动询价任务，已沿现有后端主链继续推进。");
            return existingView;
        }

        Long taskId = createAutoInquiryTask(
                ownerUserId,
                command.getDemandItemId(),
                command.getCandidateId(),
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_TASK_CREATED,
                null,
                ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                ProcurementAutoInquiryLifecycle.STAGE_CREATED,
                "已创建自动询价任务，准备进入服务端执行主链。",
                "demandItemId=" + command.getDemandItemId() + "；candidateId=" + command.getCandidateId(),
                operatorUserId
        );

        if (!Objects.equals(Boolean.FALSE, command.getTriggerDispatch())) {
            procurementAutoInquiryExecutor.execute(taskId, operatorUserId);
        }

        ProcurementAutoInquiryWorkbenchView view = buildWorkbench(
                ownerUserId,
                command.getDemandItemId(),
                command.getCandidateId()
        );
        if (view.getLatestTask() != null
                && ProcurementAutoInquiryLifecycle.STATUS_SENT.equals(
                        ProcurementAutoInquiryLifecycle.upper(view.getLatestTask().getStatus())
                )) {
            view.setMessage("自动询价已完成发送确认：SENT 状态、thread checkpoint 和消息摘要已写回。");
        } else if (view.getLatestTask() != null
                && ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY.equals(
                ProcurementAutoInquiryLifecycle.upper(view.getLatestTask().getExecutionStage())
        )) {
            view.setMessage("自动询价主链第一阶段已跑通：会话已锁定、目标已命中、输入内容已就绪。");
        }
        return view;
    }

    private Long createAutoInquiryTask(
            Long ownerUserId,
            Long demandItemId,
            Long candidateId,
            Long operatorUserId
    ) {
        synchronized (this) {
            for (int attempt = 0; attempt < 5; attempt++) {
                Long taskId = procurementMapper.nextAutoInquiryTaskId();
                while (procurementMapper.selectAutoInquiryTask(taskId) != null) {
                    taskId = taskId + 1;
                }
                try {
                    procurementMapper.insertAutoInquiryTask(
                            taskId,
                            ownerUserId,
                            demandItemId,
                            candidateId,
                            ProcurementAutoInquiryLifecycle.PLATFORM_1688,
                            ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                            ProcurementAutoInquiryLifecycle.STAGE_CREATED,
                            0,
                            3,
                            "自动询价任务已创建，等待执行器接手。",
                            operatorUserId
                    );
                    return taskId;
                } catch (DuplicateKeyException exception) {
                    if (attempt >= 4) {
                        throw exception;
                    }
                }
            }
        }
        throw new IllegalStateException("自动询价任务 ID 分配失败，请稍后重试。");
    }

    private Long resolveCandidateId(Long ownerUserId, DemandItemView demandItem, Long candidateId) {
        if (candidateId != null) {
            return candidateId;
        }
        AutoInquiryTaskView latestTask = procurementMapper.selectLatestAutoInquiryTaskByDemandItem(
                ownerUserId,
                demandItem.getId()
        );
        if (latestTask != null && latestTask.getCandidateId() != null) {
            return latestTask.getCandidateId();
        }
        if (demandItem.getSelectedCandidateId() != null) {
            return demandItem.getSelectedCandidateId();
        }
        List<CandidateView> candidates = procurementMapper.listCandidatesByDemandItem(demandItem.getId());
        return candidates.isEmpty() ? null : candidates.get(0).getId();
    }

    private DemandSnapshotView toDemandSnapshot(DemandItemView demandItem) {
        DemandSnapshotView view = new DemandSnapshotView();
        view.setId(demandItem.getId());
        view.setLineNo(demandItem.getLineNo());
        view.setSourcePlatform(demandItem.getSourcePlatform());
        view.setSourceTitle(demandItem.getSourceTitle());
        view.setSourceUrl(demandItem.getSourceUrl());
        view.setTargetSite(demandItem.getTargetSite());
        view.setStatus(demandItem.getStatus());
        return view;
    }

    private CandidateSnapshotView toCandidateSnapshot(CandidateView candidate) {
        CandidateSnapshotView view = new CandidateSnapshotView();
        view.setId(candidate.getId());
        view.setDemandItemId(candidate.getDemandItemId());
        view.setCandidatePlatform(candidate.getCandidatePlatform());
        view.setTitle(candidate.getTitle());
        view.setSupplierName(candidate.getSupplierName());
        view.setCandidateUrl(candidate.getCandidateUrl());
        view.setLevel(candidate.getLevel());
        view.setNextAction(candidate.getNextAction());
        view.setMainImageUrl(candidate.getMainImageUrl());
        view.setInquiryOpeningLine(candidate.getInquiryOpeningLine());
        view.setInquirySummaryLine(candidate.getInquirySummaryLine());
        view.setInquiryQuestions(candidate.getInquiryQuestions() == null
                ? new ArrayList<>()
                : candidate.getInquiryQuestions());
        return view;
    }

    private List<AutoInquiryEventView> loadEvents(Long taskId) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        return procurementMapper.listAutoInquiryEvents(taskId);
    }

    private void enrichTaskLabels(AutoInquiryTaskView task) {
        if (task == null) {
            return;
        }
        task.setStatusLabel(ProcurementAutoInquiryLifecycle.statusLabel(task.getStatus()));
        task.setExecutionStageLabel(ProcurementAutoInquiryLifecycle.stageLabel(task.getExecutionStage()));
    }

    private String resolveWorkbenchMessage(ProcurementAutoInquiryWorkbenchView view, CandidateView candidate) {
        if (view.getLatestTask() != null) {
            if (ProcurementAutoInquiryLifecycle.STATUS_SENT.equals(
                    ProcurementAutoInquiryLifecycle.upper(view.getLatestTask().getStatus())
            )) {
                return "当前已进入服务端自动询价发送确认阶段，可查看 SENT 结果与发送证据。";
            }
            return "当前已进入服务端自动询价主链，可直接查看会话、目标命中和输入准备状态。";
        }
        if (candidate == null) {
            return "当前需求下还没有可用于自动询价的候选商品。";
        }
        return "当前候选已准备好，可创建服务端自动询价任务。";
    }

    private Long resolveOperatorUserId(ProcurementAutoInquiryStartCommand command) {
        if (command.getOperatorUserId() != null) {
            return command.getOperatorUserId();
        }
        return command.getOwnerUserId();
    }

    @SuppressWarnings("unused")
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    @SuppressWarnings("unused")
    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }
}
