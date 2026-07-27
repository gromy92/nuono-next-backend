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
@RequestMapping("/api/procurement/auto-inquiry")
public class ProcurementAutoInquiryController {

    private final ObjectProvider<LocalDbProcurementAutoInquiryService> autoInquiryServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryReadService> readServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryCreateService> createServiceProvider;
    private final ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService> pageProbeServiceProvider;
    private final ObjectProvider<LocalDbAliUnpaidOrderCreateService> unpaidOrderServiceProvider;
    private final BusinessAccessResolver accessResolver;

    public ProcurementAutoInquiryController(
            ObjectProvider<LocalDbProcurementAutoInquiryService> autoInquiryServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryReadService> readServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreateService> createServiceProvider,
            ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService> pageProbeServiceProvider,
            ObjectProvider<LocalDbAliUnpaidOrderCreateService> unpaidOrderServiceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.autoInquiryServiceProvider = autoInquiryServiceProvider;
        this.readServiceProvider = readServiceProvider;
        this.createServiceProvider = createServiceProvider;
        this.pageProbeServiceProvider = pageProbeServiceProvider;
        this.unpaidOrderServiceProvider = unpaidOrderServiceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/workbench")
    public ProcurementAutoInquiryWorkbenchView autoInquiryWorkbench(
            @RequestParam Long ownerUserId,
            @RequestParam Long demandItemId,
            @RequestParam(required = false) Long candidateId,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = accessResolver.requireOwnerUserId(context, ownerUserId);
        LocalDbProcurementAutoInquiryService service = autoInquiryServiceProvider.getIfAvailable();
        if (service == null) {
            ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取自动询价工作台。");
            return view;
        }
        try {
            return service.buildWorkbench(authorizedOwnerUserId, demandItemId, candidateId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/start")
    public ProcurementAutoInquiryWorkbenchView startAutoInquiry(
            @RequestBody ProcurementAutoInquiryStartCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        authorizeStartCommand(command, context);
        LocalDbProcurementAutoInquiryService service = autoInquiryServiceProvider.getIfAvailable();
        if (service == null) {
            ProcurementAutoInquiryWorkbenchView view = new ProcurementAutoInquiryWorkbenchView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可创建自动询价任务。");
            return view;
        }
        try {
            return service.startAutoInquiry(
                    command.getOwnerUserId(),
                    context.getSessionUserId(),
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw badGateway(exception);
        }
    }

    @PostMapping("/ali-ai/result/probe")
    public AliAiBulkInquiryResultView probeAliAiBulkInquiryResult(
            @RequestBody(required = false) AliAiBulkInquiryResultProbeCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = authorizeProbeCommand(command, context);
        LocalDbAliAiBulkInquiryReadService service = readServiceProvider.getIfAvailable();
        if (service == null) {
            AliAiBulkInquiryResultView view = new AliAiBulkInquiryResultView();
            view.setReady(false);
            view.setReadable(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可只读验证 1688 智能询盘结果。");
            return view;
        }
        try {
            return service.probeResult(authorizedOwnerUserId, context.getSessionUserId(), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw badGateway(exception);
        }
    }

    @PostMapping("/ali-ai/create/probe")
    public AliAiBulkInquiryCreateProbeView probeAliAiBulkInquiryCreate(
            @RequestBody(required = false) AliAiBulkInquiryCreateProbeCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = authorizeProbeCommand(command, context);
        LocalDbAliAiBulkInquiryCreateService service = createServiceProvider.getIfAvailable();
        if (service == null) {
            AliAiBulkInquiryCreateProbeView view = new AliAiBulkInquiryCreateProbeView();
            view.setReady(false);
            view.setDryRun(true);
            view.setCreationAllowed(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可验证 1688 智能询盘创建计划。");
            return view;
        }
        try {
            return service.probeCreate(authorizedOwnerUserId, context.getSessionUserId(), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw badGateway(exception);
        }
    }

    @PostMapping("/ali-ai/create/page-probe")
    public AliAiBulkInquiryCreatePageProbeView probeAliAiBulkInquiryCreatePage(
            @RequestBody(required = false) AliAiBulkInquiryCreatePageProbeCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        LocalDbAliAiBulkInquiryCreatePageProbeService service = pageProbeServiceProvider.getIfAvailable();
        if (service == null) {
            AliAiBulkInquiryCreatePageProbeView view = new AliAiBulkInquiryCreatePageProbeView();
            view.setReady(false);
            view.setReadable(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可只读验证 1688 智能询盘创建页结构。");
            return view;
        }
        try {
            return service.probePage(command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw badGateway(exception);
        }
    }

    @PostMapping("/ali-unpaid-order/create/probe")
    public AliUnpaidOrderCreateProbeView probeAliUnpaidOrderCreate(
            @RequestBody(required = false) AliUnpaidOrderCreateProbeCommand command,
            @RequiredBusinessAccess(capability = BusinessCapability.PROCUREMENT)
            BusinessAccessContext context
    ) {
        Long authorizedOwnerUserId = authorizeProbeCommand(command, context);
        LocalDbAliUnpaidOrderCreateService service = unpaidOrderServiceProvider.getIfAvailable();
        if (service == null) {
            AliUnpaidOrderCreateProbeView view = new AliUnpaidOrderCreateProbeView();
            view.setReady(false);
            view.setDryRun(true);
            view.setCreationAllowed(false);
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可验证 1688 拍下未付款订单计划。");
            return view;
        }
        try {
            return service.probeCreate(authorizedOwnerUserId, context.getSessionUserId(), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw badGateway(exception);
        }
    }

    private void authorizeStartCommand(
            ProcurementAutoInquiryStartCommand command,
            BusinessAccessContext context
    ) {
        if (command == null) {
            return;
        }
        command.setOwnerUserId(accessResolver.requireOwnerUserId(context, command.getOwnerUserId()));
        command.setOperatorUserId(context.getSessionUserId());
    }

    private Long authorizeProbeCommand(
            ProcurementAutoInquiryProbeCommand command,
            BusinessAccessContext context
    ) {
        Long ownerUserId = accessResolver.requireOwnerUserId(
                context,
                command == null ? null : command.getOwnerUserId()
        );
        if (command != null) {
            command.setOwnerUserId(ownerUserId);
            command.setOperatorUserId(context.getSessionUserId());
        }
        return ownerUserId;
    }

    private ResponseStatusException badRequest(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private ResponseStatusException badGateway(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
    }
}
