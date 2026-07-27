package com.nuono.next.procurement;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
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
    private final BusinessAccessResolver accessResolver;

    public ProcurementController(
            ObjectProvider<LocalDbProcurementService> localDbProcurementServiceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.localDbProcurementServiceProvider = localDbProcurementServiceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/candidate-pool")
    public ProcurementCandidatePoolView candidatePool(
            @RequestParam Long ownerUserId,
            @RequestParam(required = false) String orderNo,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = accessResolver.requireOwnerUserId(context, ownerUserId);
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取采购候选池。");
            return view;
        }

        try {
            return procurementService.buildCandidatePool(authorizedOwnerUserId, orderNo);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/select-candidate")
    public ProcurementCandidatePoolView selectCandidate(
            @RequestBody ProcurementDecisionCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ProcurementCandidatePoolWriteContext writeContext =
                authorizeWrite(context, command == null ? null : command.getOwnerUserId());
        if (command != null) {
            command.setOwnerUserId(writeContext.ownerUserId);
        }
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可提交采购决策。");
            return view;
        }

        try {
            return procurementService.selectCandidate(writeContext, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/review-candidate")
    public ProcurementCandidatePoolView reviewCandidate(
            @RequestBody ProcurementCandidateReviewCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ProcurementCandidatePoolWriteContext writeContext =
                authorizeWrite(context, command == null ? null : command.getOwnerUserId());
        if (command != null) {
            command.setOwnerUserId(writeContext.ownerUserId);
        }
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可保存人工判断。");
            return view;
        }

        try {
            return procurementService.saveCandidateReview(writeContext, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/run-auto-selection")
    public ProcurementCandidatePoolView runAutoSelection(
            @RequestBody ProcurementAutoSelectionCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ProcurementCandidatePoolWriteContext writeContext =
                authorizeWrite(context, command == null ? null : command.getOwnerUserId());
        if (command != null) {
            command.setOwnerUserId(writeContext.ownerUserId);
        }
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可运行自动选品。");
            return view;
        }

        try {
            return procurementService.runAutoSelection(writeContext, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/preview-extraction")
    public ProcurementExtractionPreviewView previewExtraction(
            @RequestBody ProcurementExtractionPreviewCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
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
    public ProcurementSearchPagePreviewView previewSearchPage(
            @RequestBody ProcurementSearchPagePreviewCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
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
            @RequestBody ProcurementImportSearchPageCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ProcurementCandidatePoolWriteContext writeContext =
                authorizeWrite(context, command == null ? null : command.getOwnerUserId());
        if (command != null) {
            command.setOwnerUserId(writeContext.ownerUserId);
        }
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可导入 1688 搜索页候选。");
            return view;
        }

        try {
            return procurementService.importSearchPageCandidates(writeContext, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/backfill-candidates")
    public ProcurementCandidatePoolView backfillCandidates(
            @RequestBody ProcurementManualCandidateBackfillCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ProcurementCandidatePoolWriteContext writeContext =
                authorizeWrite(context, command == null ? null : command.getOwnerUserId());
        if (command != null) {
            command.setOwnerUserId(writeContext.ownerUserId);
        }
        LocalDbProcurementService procurementService = localDbProcurementServiceProvider.getIfAvailable();
        if (procurementService == null) {
            ProcurementCandidatePoolView view = new ProcurementCandidatePoolView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可回填 1688 候选。");
            return view;
        }

        try {
            return procurementService.backfillManualCandidates(writeContext, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    private ProcurementCandidatePoolWriteContext authorizeWrite(
            BusinessAccessContext context,
            Long requestedOwnerUserId
    ) {
        Long ownerUserId = accessResolver.requireOwnerUserId(context, requestedOwnerUserId);
        Long operatorUserId = context == null ? null : context.getSessionUserId();
        String operatorRole = context == null ? null : context.getRoleName();
        return new ProcurementCandidatePoolWriteContext(ownerUserId, operatorUserId, operatorRole);
    }
}
