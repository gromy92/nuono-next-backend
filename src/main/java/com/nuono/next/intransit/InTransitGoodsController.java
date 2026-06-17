package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightSyncView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/in-transit-goods")
public class InTransitGoodsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InTransitGoodsController.class);

    private final InTransitBatchService batchService;
    private final InTransitPluginSyncService pluginSyncService;
    private final InTransitFreightCostService freightCostService;
    private final BusinessAccessResolver businessAccessResolver;
    private final InTransitGoodsAccessScopeService accessScopeService;
    private final InTransitManualEditGuard manualEditGuard = new InTransitManualEditGuard();

    public InTransitGoodsController(
            InTransitBatchService batchService,
            InTransitPluginSyncService pluginSyncService,
            InTransitFreightCostService freightCostService,
            BusinessAccessResolver businessAccessResolver,
            InTransitGoodsAccessScopeService accessScopeService
    ) {
        this.batchService = batchService;
        this.pluginSyncService = pluginSyncService;
        this.freightCostService = freightCostService;
        this.businessAccessResolver = businessAccessResolver;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping("/batches")
    public BatchListView batches(
            InTransitBatchQuery query,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        InTransitBatchQuery resolved = query == null ? new InTransitBatchQuery() : query;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        try {
            accessScopeService.applyReadableBatchScope(context, resolved);
            return batchService.listBatches(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/batches/{batchId}")
    public BatchView batch(@PathVariable Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = requireContext(request);
        try {
            BatchView view = batchService.getBatch(context.getBusinessOwnerUserId(), batchId);
            accessScopeService.requireBatchAccess(context, view);
            return view;
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/batches")
    public BatchView saveBatch(
            @RequestBody(required = false) SaveBatchCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveBatchCommand resolved = command == null ? new SaveBatchCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        try {
            if (resolved.getBatchId() != null) {
                BatchView existingBatch = batchService.getBatch(context.getBusinessOwnerUserId(), resolved.getBatchId());
                accessScopeService.requireBatchAccess(context, existingBatch);
                manualEditGuard.requireBatchBaseEditable(existingBatch);
            }
            accessScopeService.requireWritableBatchScope(context, resolved);
            return batchService.saveBatch(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @GetMapping("/batches/{batchId}/lines")
    public LineListView lines(@PathVariable Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = requireContext(request);
        try {
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            return batchService.listLines(context.getBusinessOwnerUserId(), batchId);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/batches/{batchId}/lines")
    public LineView saveLine(
            @PathVariable Long batchId,
            @RequestBody(required = false) SaveLineCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveLineCommand resolved = command == null ? new SaveLineCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        resolved.setBatchId(batchId);
        try {
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            accessScopeService.requireWritableLineScope(context, resolved);
            return batchService.saveLine(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/batches/{batchId}/lines/{lineId}")
    public LineListView deleteLine(
            @PathVariable Long batchId,
            @PathVariable Long lineId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        DeleteLineCommand command = new DeleteLineCommand();
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setBatchId(batchId);
        command.setLineId(lineId);
        try {
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            return batchService.deleteLine(command);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/batches/{batchId}/nodes")
    public NodeListView nodes(@PathVariable Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = requireContext(request);
        try {
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            return batchService.listNodes(context.getBusinessOwnerUserId(), batchId);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/batches/{batchId}/nodes")
    public NodeView saveNode(
            @PathVariable Long batchId,
            @RequestBody(required = false) SaveNodeCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveNodeCommand resolved = command == null ? new SaveNodeCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        resolved.setBatchId(batchId);
        try {
            BatchView existingBatch = batchService.getBatch(context.getBusinessOwnerUserId(), batchId);
            accessScopeService.requireBatchAccess(context, existingBatch);
            manualEditGuard.requireLogisticsNodesEditable(existingBatch);
            return batchService.saveNode(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/batches/{batchId}/nodes/{nodeId}")
    public NodeListView deleteNode(
            @PathVariable Long batchId,
            @PathVariable Long nodeId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        DeleteNodeCommand command = new DeleteNodeCommand();
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setBatchId(batchId);
        command.setNodeId(nodeId);
        try {
            BatchView existingBatch = batchService.getBatch(context.getBusinessOwnerUserId(), batchId);
            accessScopeService.requireBatchAccess(context, existingBatch);
            manualEditGuard.requireLogisticsNodesEditable(existingBatch);
            return batchService.deleteNode(command);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/plugin-sync/preview")
    public PluginSyncPreviewView previewPluginSync(
            @RequestBody(required = false) PluginSyncCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        PluginSyncCommand resolved = command == null ? new PluginSyncCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        resolved.setAccessContext(context);
        logPluginSyncRequest("preview", resolved, context);
        try {
            return pluginSyncService.preview(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/plugin-sync/commit")
    public PluginSyncCommitView commitPluginSync(
            @RequestBody(required = false) PluginSyncCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        PluginSyncCommand resolved = command == null ? new PluginSyncCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        resolved.setAccessContext(context);
        logPluginSyncRequest("commit", resolved, context);
        try {
            return pluginSyncService.commit(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/freight-costs/plugin-sync")
    public ActualFreightSyncView syncActualFreightCosts(
            @RequestBody(required = false) ActualFreightSyncCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        ActualFreightSyncCommand resolved = command == null ? new ActualFreightSyncCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        resolved.setAccessContext(context);
        try {
            return freightCostService.syncActualCosts(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private void logPluginSyncRequest(String action, PluginSyncCommand command, BusinessAccessContext context) {
        List<PluginSyncBatch> batches = command.getBatches();
        int packageCount = batches.stream()
                .mapToInt(batch -> batch.getPackages().size())
                .sum();
        int lineCount = batches.stream()
                .flatMap(batch -> batch.getPackages().stream())
                .mapToInt(itemPackage -> itemPackage.getLines().size())
                .sum();
        String batchNos = batches.stream()
                .map(PluginSyncBatch::getBatchNo)
                .filter(batchNo -> batchNo != null && !batchNo.trim().isEmpty())
                .limit(20)
                .collect(Collectors.joining(","));
        LOGGER.info(
                "inTransit plugin-sync {} received ownerUserId={} operatorUserId={} sourceSystem={} batchCount={} packageCount={} lineCount={} batchNos={}",
                action,
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                command.getSourceSystem(),
                batches.size(),
                packageCount,
                lineCount,
                batchNos
        );
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }
}
