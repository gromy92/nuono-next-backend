package com.nuono.next.procurement;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AddPoolCandidateCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AdvancePoolItemFollowUpCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.ConfirmFinalCandidatesCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.GenerateSummaryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.InitializePoolCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.MarkPoolItemExceptionCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.OperatorCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.RemovePoolItemCommand;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/procurement/requirement-confirmation")
public class ProcurementRequirementConfirmationController {

    private final ObjectProvider<LocalDbProcurementRequirementConfirmationService> requirementConfirmationServiceProvider;
    private final ObjectProvider<LocalDbProcurementCandidatePoolService> candidatePoolServiceProvider;
    private final BusinessAccessResolver businessAccessResolver;

    public ProcurementRequirementConfirmationController(
            ObjectProvider<LocalDbProcurementRequirementConfirmationService> requirementConfirmationServiceProvider,
            ObjectProvider<LocalDbProcurementCandidatePoolService> candidatePoolServiceProvider,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.requirementConfirmationServiceProvider = requirementConfirmationServiceProvider;
        this.candidatePoolServiceProvider = candidatePoolServiceProvider;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/demands")
    public ProcurementRequirementConfirmationListView demands(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = businessAccessResolver.requireOwnerUserId(context, ownerUserId);
        LocalDbProcurementRequirementConfirmationService service = requirementConfirmationServiceProvider.getIfAvailable();
        if (service == null) {
            ProcurementRequirementConfirmationListView view = new ProcurementRequirementConfirmationListView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取采购需求确认列表。");
            return view;
        }

        try {
            return service.listDemands(authorizedOwnerUserId, status, keyword, page, pageSize);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/demands/{demandItemId}")
    public ProcurementRequirementConfirmationDetailView demand(
            @PathVariable Long demandItemId,
            @RequestParam(required = false) Long ownerUserId,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = businessAccessResolver.requireOwnerUserId(context, ownerUserId);
        LocalDbProcurementRequirementConfirmationService service = requirementConfirmationServiceProvider.getIfAvailable();
        if (service == null) {
            return detailBootstrap("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取采购需求确认详情。");
        }

        try {
            return service.getDemandDetail(demandItemId, authorizedOwnerUserId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/demands/{demandItemId}/pool/initialize")
    public ProcurementRequirementConfirmationDetailView initializePool(
            @PathVariable Long demandItemId,
            @RequestBody(required = false) InitializePoolCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        InitializePoolCommand authorizedCommand = authorizeCommand(context, command, InitializePoolCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可初始化待选池。",
                (service) -> service.initializePool(demandItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/items/{poolItemId}/remove")
    public ProcurementRequirementConfirmationDetailView removePoolItem(
            @PathVariable Long demandItemId,
            @PathVariable Long poolItemId,
            @RequestBody(required = false) RemovePoolItemCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        RemovePoolItemCommand authorizedCommand = authorizeCommand(context, command, RemovePoolItemCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可移出待选池候选。",
                (service) -> service.removePoolItem(demandItemId, poolItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/candidates/{candidateId}/add")
    public ProcurementRequirementConfirmationDetailView addPoolCandidate(
            @PathVariable Long demandItemId,
            @PathVariable Long candidateId,
            @RequestBody(required = false) AddPoolCandidateCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        AddPoolCandidateCommand authorizedCommand = authorizeCommand(context, command, AddPoolCandidateCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可补入备选候选。",
                (service) -> service.addCandidateToPool(demandItemId, candidateId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/inquiry/finish")
    public ProcurementRequirementConfirmationDetailView finishInquiry(
            @PathVariable Long demandItemId,
            @RequestBody(required = false) FinishPoolInquiryCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        FinishPoolInquiryCommand authorizedCommand = authorizeCommand(context, command, FinishPoolInquiryCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可收口自动询价。",
                (service) -> service.finishInquiry(demandItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/items/{poolItemId}/reply")
    public ProcurementRequirementConfirmationDetailView recordPoolItemReply(
            @PathVariable Long demandItemId,
            @PathVariable Long poolItemId,
            @RequestBody(required = false) RecordPoolItemReplyCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        RecordPoolItemReplyCommand authorizedCommand =
                authorizeCommand(context, command, RecordPoolItemReplyCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可记录供应商回复。",
                (service) -> service.recordPoolItemReply(demandItemId, poolItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/items/{poolItemId}/follow-up/advance")
    public ProcurementRequirementConfirmationDetailView advancePoolItemFollowUp(
            @PathVariable Long demandItemId,
            @PathVariable Long poolItemId,
            @RequestBody(required = false) AdvancePoolItemFollowUpCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        AdvancePoolItemFollowUpCommand authorizedCommand =
                authorizeCommand(context, command, AdvancePoolItemFollowUpCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可推进催发状态。",
                (service) -> service.advancePoolItemFollowUp(demandItemId, poolItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/items/{poolItemId}/no-reply-handoff")
    public ProcurementRequirementConfirmationDetailView markPoolItemNoReplyHandoff(
            @PathVariable Long demandItemId,
            @PathVariable Long poolItemId,
            @RequestBody(required = false) MarkPoolItemExceptionCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        MarkPoolItemExceptionCommand authorizedCommand =
                authorizeCommand(context, command, MarkPoolItemExceptionCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可标记 24 小时无回复。",
                (service) -> service.markNoReplyHandoff(demandItemId, poolItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/pool/items/{poolItemId}/reply-parse-failed")
    public ProcurementRequirementConfirmationDetailView markPoolItemReplyParseFailed(
            @PathVariable Long demandItemId,
            @PathVariable Long poolItemId,
            @RequestBody(required = false) MarkPoolItemExceptionCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        MarkPoolItemExceptionCommand authorizedCommand =
                authorizeCommand(context, command, MarkPoolItemExceptionCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可标记回复解析失败。",
                (service) -> service.markReplyParseFailure(demandItemId, poolItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/final-candidates/confirm")
    public ProcurementRequirementConfirmationDetailView confirmFinalCandidates(
            @PathVariable Long demandItemId,
            @RequestBody(required = false) ConfirmFinalCandidatesCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        ConfirmFinalCandidatesCommand authorizedCommand =
                authorizeCommand(context, command, ConfirmFinalCandidatesCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可确认最终 2 个。",
                (service) -> service.confirmFinalCandidates(demandItemId, authorizedCommand)
        );
    }

    @PostMapping("/demands/{demandItemId}/summary/generate")
    public ProcurementRequirementConfirmationDetailView generateSummary(
            @PathVariable Long demandItemId,
            @RequestBody(required = false) GenerateSummaryCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        GenerateSummaryCommand authorizedCommand = authorizeCommand(context, command, GenerateSummaryCommand::new);
        return withPoolService(
                "当前仍在无数据库骨架模式。切换到 local-db profile 后可生成 AI 总结。",
                (service) -> service.generateSummary(demandItemId, authorizedCommand)
        );
    }

    private ProcurementRequirementConfirmationDetailView withPoolService(
            String bootstrapMessage,
            PoolServiceCall call
    ) {
        LocalDbProcurementCandidatePoolService service = candidatePoolServiceProvider.getIfAvailable();
        if (service == null) {
            return detailBootstrap(bootstrapMessage);
        }

        try {
            return call.execute(service);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private ProcurementRequirementConfirmationDetailView detailBootstrap(String message) {
        ProcurementRequirementConfirmationDetailView view = new ProcurementRequirementConfirmationDetailView();
        view.setMode("bootstrap-only");
        view.setReady(false);
        view.setMessage(message);
        return view;
    }

    private <T extends OperatorCommand> T authorizeCommand(
            BusinessAccessContext context,
            T command,
            Supplier<T> emptyCommandFactory
    ) {
        T authorizedCommand = command == null ? emptyCommandFactory.get() : command;
        Long ownerUserId = businessAccessResolver.requireOwnerUserId(
                context,
                authorizedCommand.getOwnerUserId()
        );
        authorizedCommand.setOwnerUserId(ownerUserId);
        authorizedCommand.setOperatorUserId(context.getSessionUserId());
        authorizedCommand.setOperatorRole(context.getRoleName());
        return authorizedCommand;
    }

    @FunctionalInterface
    private interface PoolServiceCall {
        ProcurementRequirementConfirmationDetailView execute(LocalDbProcurementCandidatePoolService service);
    }
}
