package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderAliasCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/in-transit-goods")
public class InTransitForwarderController {

    private final InTransitForwarderService forwarderService;
    private final BusinessAccessResolver businessAccessResolver;

    public InTransitForwarderController(
            InTransitForwarderService forwarderService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.forwarderService = forwarderService;
        this.businessAccessResolver = businessAccessResolver;
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
