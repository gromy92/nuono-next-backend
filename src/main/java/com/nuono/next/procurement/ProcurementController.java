package com.nuono.next.procurement;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
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
    private final BusinessAccessResolver businessAccessResolver;

    public ProcurementController(
            ObjectProvider<LocalDbProcurementService> localDbProcurementServiceProvider,
            ObjectProvider<LocalDbProcurementAutoInquiryService> localDbProcurementAutoInquiryServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryReadService> localDbAliAiBulkInquiryReadServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreateService> localDbAliAiBulkInquiryCreateServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService>
                    localDbAliAiBulkInquiryCreatePageProbeServiceProvider,
            ObjectProvider<LocalDbAliUnpaidOrderCreateService> localDbAliUnpaidOrderCreateServiceProvider,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.localDbProcurementServiceProvider = localDbProcurementServiceProvider;
        this.localDbProcurementAutoInquiryServiceProvider = localDbProcurementAutoInquiryServiceProvider;
        this.localDbAliAiBulkInquiryReadServiceProvider = localDbAliAiBulkInquiryReadServiceProvider;
        this.localDbAliAiBulkInquiryCreateServiceProvider = localDbAliAiBulkInquiryCreateServiceProvider;
        this.localDbAliAiBulkInquiryCreatePageProbeServiceProvider =
                localDbAliAiBulkInquiryCreatePageProbeServiceProvider;
        this.localDbAliUnpaidOrderCreateServiceProvider = localDbAliUnpaidOrderCreateServiceProvider;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/candidate-pool")
    public ProcurementCandidatePoolView candidatePool(
            @RequestParam(required = false) Long ignoredOwnerUserId,
            @RequestParam(required = false) String orderNo,
            HttpServletRequest request
    ) {
        Long ownerUserId = trustedProcurementOwnerUserId(request);
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
    public ProcurementCandidatePoolView selectCandidate(
            @RequestBody(required = false) ProcurementDecisionCommand command,
            HttpServletRequest request
    ) {
        ProcurementDecisionCommand trustedCommand = trustedDecisionCommand(command, request);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可提交采购决策。");
            return view;
        }

        try {
            return procurementService.selectCandidate(trustedCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/review-candidate")
    public ProcurementCandidatePoolView reviewCandidate(
            @RequestBody(required = false) ProcurementCandidateReviewCommand command,
            HttpServletRequest request
    ) {
        ProcurementCandidateReviewCommand trustedCommand = trustedReviewCommand(command, request);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可保存人工判断。");
            return view;
        }

        try {
            return procurementService.saveCandidateReview(trustedCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/run-auto-selection")
    public ProcurementCandidatePoolView runAutoSelection(
            @RequestBody(required = false) ProcurementAutoSelectionCommand command,
            HttpServletRequest request
    ) {
        ProcurementAutoSelectionCommand trustedCommand = trustedAutoSelectionCommand(command, request);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可运行自动选品。");
            return view;
        }

        try {
            return procurementService.runAutoSelection(trustedCommand);
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
    public ProcurementCandidatePoolView importSearchPage(
            @RequestBody(required = false) ProcurementImportSearchPageCommand command,
            HttpServletRequest request
    ) {
        ProcurementImportSearchPageCommand trustedCommand = trustedImportSearchPageCommand(command, request);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可导入 1688 搜索页候选。");
            return view;
        }

        try {
            return procurementService.importSearchPageCandidates(trustedCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/backfill-candidates")
    public ProcurementCandidatePoolView backfillCandidates(
            @RequestBody(required = false) ProcurementManualCandidateBackfillCommand command,
            HttpServletRequest request
    ) {
        ProcurementManualCandidateBackfillCommand trustedCommand = trustedBackfillCommand(command, request);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可回填 1688 候选。");
            return view;
        }

        try {
            return procurementService.backfillManualCandidates(trustedCommand);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @GetMapping("/auto-inquiry/workbench")
    public ProcurementAutoInquiryWorkbenchView autoInquiryWorkbench(
            @RequestParam(required = false) Long ignoredOwnerUserId,
            @RequestParam Long demandItemId,
            @RequestParam(required = false) Long candidateId,
            HttpServletRequest request
    ) {
        Long ownerUserId = trustedProcurementOwnerUserId(request);
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
    public ProcurementAutoInquiryWorkbenchView startAutoInquiry(
            @RequestBody(required = false) ProcurementAutoInquiryStartCommand command,
            HttpServletRequest request
    ) {
        ProcurementAutoInquiryStartCommand trustedCommand = trustedAutoInquiryStartCommand(command, request);
        LocalDbProcurementAutoInquiryService autoInquiryService = localDbProcurementAutoInquiryServiceProvider.getIfAvailable();
        if (autoInquiryService == null) {
            ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可创建自动询价任务。");
            return view;
        }

        try {
            return autoInquiryService.startAutoInquiry(trustedCommand);
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

    private BusinessAccessContext requireProcurementAccess(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT);
    }

    private Long trustedProcurementOwnerUserId(HttpServletRequest request) {
        return requireProcurementAccess(request).getBusinessOwnerUserId();
    }

    private ProcurementDecisionCommand trustedDecisionCommand(
            ProcurementDecisionCommand command,
            HttpServletRequest request
    ) {
        ProcurementDecisionCommand source = command == null ? new ProcurementDecisionCommand() : command;
        source.setOwnerUserId(trustedProcurementOwnerUserId(request));
        return source;
    }

    private ProcurementCandidateReviewCommand trustedReviewCommand(
            ProcurementCandidateReviewCommand command,
            HttpServletRequest request
    ) {
        ProcurementCandidateReviewCommand source = command == null ? new ProcurementCandidateReviewCommand() : command;
        source.setOwnerUserId(trustedProcurementOwnerUserId(request));
        return source;
    }

    private ProcurementAutoSelectionCommand trustedAutoSelectionCommand(
            ProcurementAutoSelectionCommand command,
            HttpServletRequest request
    ) {
        ProcurementAutoSelectionCommand source = command == null ? new ProcurementAutoSelectionCommand() : command;
        source.setOwnerUserId(trustedProcurementOwnerUserId(request));
        return source;
    }

    private ProcurementImportSearchPageCommand trustedImportSearchPageCommand(
            ProcurementImportSearchPageCommand command,
            HttpServletRequest request
    ) {
        ProcurementImportSearchPageCommand source = command == null ? new ProcurementImportSearchPageCommand() : command;
        source.setOwnerUserId(trustedProcurementOwnerUserId(request));
        return source;
    }

    private ProcurementManualCandidateBackfillCommand trustedBackfillCommand(
            ProcurementManualCandidateBackfillCommand command,
            HttpServletRequest request
    ) {
        ProcurementManualCandidateBackfillCommand source = command == null
                ? new ProcurementManualCandidateBackfillCommand()
                : command;
        source.setOwnerUserId(trustedProcurementOwnerUserId(request));
        return source;
    }

    private ProcurementAutoInquiryStartCommand trustedAutoInquiryStartCommand(
            ProcurementAutoInquiryStartCommand command,
            HttpServletRequest request
    ) {
        ProcurementAutoInquiryStartCommand source = command == null ? new ProcurementAutoInquiryStartCommand() : command;
        BusinessAccessContext context = requireProcurementAccess(request);
        source.setOwnerUserId(context.getBusinessOwnerUserId());
        source.setOperatorUserId(context.getSessionUserId());
        return source;
    }
}
