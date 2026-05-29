package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderAliasCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/in-transit-goods")
public class InTransitGoodsController {

    private final InTransitForwarderService forwarderService;
    private final InTransitBatchService batchService;
    private final InTransitImportService importService;
    private final BusinessAccessResolver businessAccessResolver;
    private final InTransitGoodsAccessScopeService accessScopeService;

    public InTransitGoodsController(
            InTransitForwarderService forwarderService,
            InTransitBatchService batchService,
            InTransitImportService importService,
            BusinessAccessResolver businessAccessResolver,
            InTransitGoodsAccessScopeService accessScopeService
    ) {
        this.forwarderService = forwarderService;
        this.batchService = batchService;
        this.importService = importService;
        this.businessAccessResolver = businessAccessResolver;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping("/contracts")
    public InTransitContractView contracts(HttpServletRequest request) {
        requireContext(request);
        return forwarderService.contract();
    }

    @GetMapping("/forwarders")
    public List<ForwarderView> forwarders(HttpServletRequest request) {
        BusinessAccessContext context = requireContext(request);
        return forwarderService.listForwarders(context.getBusinessOwnerUserId());
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
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            return batchService.saveNode(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/import-preview")
    public ImportPreviewView previewImport(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        PreviewImportCommand command = new PreviewImportCommand();
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setAccessContext(context);
        command.setFileName(file == null ? null : file.getOriginalFilename());
        command.setContentType(file == null ? null : file.getContentType());
        try {
            command.setContent(file == null ? null : file.getBytes());
            return importService.preview(command);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入文件读取失败。", exception);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(HttpServletRequest request) {
        requireContext(request);
        byte[] template = importService.buildTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"in-transit-goods-import-template.xlsx\"")
                .body(template);
    }

    @PostMapping("/imports/{importBatchId}/confirm")
    public ImportConfirmView confirmImport(
            @PathVariable Long importBatchId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setImportBatchId(importBatchId);
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setAccessContext(context);
        try {
            return importService.confirm(command);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/forwarders")
    public ForwarderView saveForwarder(
            @RequestBody(required = false) SaveForwarderCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveForwarderCommand resolved = command == null ? new SaveForwarderCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        try {
            return forwarderService.saveForwarder(resolved);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/forwarder-aliases")
    public ForwarderAliasView saveForwarderAlias(
            @RequestBody(required = false) SaveForwarderAliasCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveForwarderAliasCommand resolved = command == null ? new SaveForwarderAliasCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        try {
            return forwarderService.saveForwarderAlias(resolved);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/forwarder-aliases/resolve")
    public ForwarderResolveView resolveForwarder(
            @RequestBody(required = false) ResolveForwarderCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        ResolveForwarderCommand resolved = command == null ? new ResolveForwarderCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        try {
            return forwarderService.resolveForwarder(resolved);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }
}
