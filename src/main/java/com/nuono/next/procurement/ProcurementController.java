package com.nuono.next.procurement;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/procurement")
public class ProcurementController {

    private final ObjectProvider<LocalDbProcurementService> localDbProcurementServiceProvider;
    private final ObjectProvider<LocalDbProcurementAutoInquiryService> localDbProcurementAutoInquiryServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryReadService> localDbAliAiBulkInquiryReadServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryCreateService> localDbAliAiBulkInquiryCreateServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService>
            localDbAliAiBulkInquiryCreatePageProbeServiceProvider;
    private final ObjectProvider<LocalDbAliUnpaidOrderCreateService> localDbAliUnpaidOrderCreateServiceProvider;

    public ProcurementController(
            ObjectProvider<LocalDbProcurementService> localDbProcurementServiceProvider,
            ObjectProvider<LocalDbProcurementAutoInquiryService> localDbProcurementAutoInquiryServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryReadService> localDbAliAiBulkInquiryReadServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreateService> localDbAliAiBulkInquiryCreateServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService>
                    localDbAliAiBulkInquiryCreatePageProbeServiceProvider,
            ObjectProvider<LocalDbAliUnpaidOrderCreateService> localDbAliUnpaidOrderCreateServiceProvider
    ) {
        this.localDbProcurementServiceProvider = localDbProcurementServiceProvider;
        this.localDbProcurementAutoInquiryServiceProvider = localDbProcurementAutoInquiryServiceProvider;
        this.localDbAliAiBulkInquiryReadServiceProvider = localDbAliAiBulkInquiryReadServiceProvider;
        this.localDbAliAiBulkInquiryCreateServiceProvider = localDbAliAiBulkInquiryCreateServiceProvider;
        this.localDbAliAiBulkInquiryCreatePageProbeServiceProvider =
                localDbAliAiBulkInquiryCreatePageProbeServiceProvider;
        this.localDbAliUnpaidOrderCreateServiceProvider = localDbAliUnpaidOrderCreateServiceProvider;
    }

    @GetMapping("/candidate-pool")
    public ProcurementCandidatePoolView candidatePool(
            @RequestParam Long ownerUserId,
            @RequestParam(required = false) String orderNo
    ) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取采购候选池。");
            return view;
        }

        try {
            return procurementService.buildCandidatePool(ownerUserId, orderNo);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/select-candidate")
    public ProcurementCandidatePoolView selectCandidate(@RequestBody ProcurementDecisionCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可提交采购决策。");
            return view;
        }

        try {
            return procurementService.selectCandidate(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/review-candidate")
    public ProcurementCandidatePoolView reviewCandidate(@RequestBody ProcurementCandidateReviewCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可保存人工判断。");
            return view;
        }

        try {
            return procurementService.saveCandidateReview(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/run-auto-selection")
    public ProcurementCandidatePoolView runAutoSelection(@RequestBody ProcurementAutoSelectionCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可运行自动选品。");
            return view;
        }

        try {
            return procurementService.runAutoSelection(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/preview-extraction")
    public ProcurementExtractionPreviewView previewExtraction(@RequestBody ProcurementExtractionPreviewCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementExtractionPreviewView view = new ProcurementExtractionPreviewView();
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可预览 1688 字段抽取。");
            return view;
        }

        try {
            return procurementService.previewExtraction(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/preview-search-page")
    public ProcurementSearchPagePreviewView previewSearchPage(@RequestBody ProcurementSearchPagePreviewCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementSearchPagePreviewView view = new ProcurementSearchPagePreviewView();
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可预览 1688 搜索页抽取。");
            return view;
        }

        try {
            return procurementService.previewSearchPageExtraction(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/import-search-page")
    public ProcurementCandidatePoolView importSearchPage(@RequestBody ProcurementImportSearchPageCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可导入 1688 搜索页候选。");
            return view;
        }

        try {
            return procurementService.importSearchPageCandidates(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/backfill-candidates")
    public ProcurementCandidatePoolView backfillCandidates(@RequestBody ProcurementManualCandidateBackfillCommand command) {
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可回填 1688 候选。");
            return view;
        }

        try {
            return procurementService.backfillManualCandidates(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @GetMapping("/auto-inquiry/workbench")
    public ProcurementAutoInquiryWorkbenchView autoInquiryWorkbench(
            @RequestParam Long ownerUserId,
            @RequestParam Long demandItemId,
            @RequestParam(required = false) Long candidateId
    ) {
        LocalDbProcurementAutoInquiryService autoInquiryService = localDbProcurementAutoInquiryServiceProvider.getIfAvailable();
        if (autoInquiryService == null) {
            ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取自动询价工作台。");
            return view;
        }

        try {
            return autoInquiryService.buildWorkbench(ownerUserId, demandItemId, candidateId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/auto-inquiry/start")
    public ProcurementAutoInquiryWorkbenchView startAutoInquiry(@RequestBody ProcurementAutoInquiryStartCommand command) {
        LocalDbProcurementAutoInquiryService autoInquiryService = localDbProcurementAutoInquiryServiceProvider.getIfAvailable();
        if (autoInquiryService == null) {
            ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可创建自动询价任务。");
            return view;
        }

        try {
            return autoInquiryService.startAutoInquiry(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/auto-inquiry/ali-ai/result/probe")
    public AliAiBulkInquiryResultView probeAliAiBulkInquiryResult(
            @RequestBody(required = false) AliAiBulkInquiryResultProbeCommand command
    ) {
        LocalDbAliAiBulkInquiryReadService readService = localDbAliAiBulkInquiryReadServiceProvider.getIfAvailable();
        if (readService == null) {
            AliAiBulkInquiryResultView view = new AliAiBulkInquiryResultView();
            view.setReady(false);
            view.setReadable(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可只读验证 1688 智能询盘结果。");
            return view;
        }

        try {
            return readService.probeResult(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/auto-inquiry/ali-ai/create/probe")
    public AliAiBulkInquiryCreateProbeView probeAliAiBulkInquiryCreate(
            @RequestBody(required = false) AliAiBulkInquiryCreateProbeCommand command
    ) {
        LocalDbAliAiBulkInquiryCreateService createService = localDbAliAiBulkInquiryCreateServiceProvider.getIfAvailable();
        if (createService == null) {
            AliAiBulkInquiryCreateProbeView view = new AliAiBulkInquiryCreateProbeView();
            view.setReady(false);
            view.setDryRun(true);
            view.setCreationAllowed(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可验证 1688 智能询盘创建计划。");
            return view;
        }

        try {
            return createService.probeCreate(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/auto-inquiry/ali-ai/create/page-probe")
    public AliAiBulkInquiryCreatePageProbeView probeAliAiBulkInquiryCreatePage(
            @RequestBody(required = false) AliAiBulkInquiryCreatePageProbeCommand command
    ) {
        LocalDbAliAiBulkInquiryCreatePageProbeService pageProbeService =
                localDbAliAiBulkInquiryCreatePageProbeServiceProvider.getIfAvailable();
        if (pageProbeService == null) {
            AliAiBulkInquiryCreatePageProbeView view = new AliAiBulkInquiryCreatePageProbeView();
            view.setReady(false);
            view.setReadable(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可只读验证 1688 智能询盘创建页结构。");
            return view;
        }

        try {
            return pageProbeService.probePage(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/auto-inquiry/ali-unpaid-order/create/probe")
    public AliUnpaidOrderCreateProbeView probeAliUnpaidOrderCreate(
            @RequestBody(required = false) AliUnpaidOrderCreateProbeCommand command
    ) {
        LocalDbAliUnpaidOrderCreateService createService = localDbAliUnpaidOrderCreateServiceProvider.getIfAvailable();
        if (createService == null) {
            AliUnpaidOrderCreateProbeView view = new AliUnpaidOrderCreateProbeView();
            view.setReady(false);
            view.setDryRun(true);
            view.setCreationAllowed(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可验证 1688 拍下未付款订单计划。");
            return view;
        }

        try {
            return createService.probeCreate(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }
}
