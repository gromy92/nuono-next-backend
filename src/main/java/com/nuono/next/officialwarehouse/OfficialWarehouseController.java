package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noonlog.NoonHttpCallLogView;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CorrectAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.SyncNoonAsnNumbersCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentAvailabilityView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ShippingBatchCandidateView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.web.ApiProblemException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/warehouse/official-warehouse")
public class OfficialWarehouseController {

    private final ObjectProvider<LocalDbOfficialWarehouseService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseController(
            ObjectProvider<LocalDbOfficialWarehouseService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/asns")
    public List<AsnView> listAsns(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listAsns(access(request), storeCode, siteCode, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/asns/{asnId}")
    public AsnView getAsn(
            @PathVariable String asnId,
            HttpServletRequest request
    ) {
        try {
            return service().getAsn(access(request), asnId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/asns/sync-noon-list")
    public AsnListSyncView syncNoonAsnList(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        try {
            return service().syncNoonAsnList(storeAccess(request, storeCode), storeCode, siteCode);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/asns/sync-noon-numbers")
    public AsnListSyncView syncNoonAsnNumbers(
            @RequestBody SyncNoonAsnNumbersCommand command,
            HttpServletRequest request
    ) {
        try {
            if (command == null) {
                throw new IllegalArgumentException("缺少 ASN 定向同步参数。");
            }
            BusinessAccessContext access = storeAccess(request, command.storeCode);
            boolean dryRun = command.dryRun == null || command.dryRun;
            return service().syncNoonAsnNumbers(access, command.storeCode, command.siteCode, command.asnNumbers, dryRun);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/asns")
    public AsnView createAsn(
            @RequestBody CreateAsnCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext access = storeAccess(request, command == null ? null : command.storeCode);
            return service().createAsn(access, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @GetMapping("/product-candidates")
    public List<ProductCandidateView> productCandidates(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> shippingBatchIds,
            HttpServletRequest request
    ) {
        try {
            return service().listProductCandidates(storeAccess(request, storeCode), storeCode, siteCode, keyword, shippingBatchIds);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-batches")
    public List<ShippingBatchCandidateView> shippingBatches(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listShippingBatchCandidates(storeAccess(request, storeCode), storeCode, siteCode, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/asns/{asnId}/noon-calls")
    public List<NoonHttpCallLogView> noonCalls(
            @PathVariable String asnId,
            HttpServletRequest request
    ) {
        try {
            return service().listNoonCalls(access(request), asnId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/appointments")
    public List<AppointmentView> appointments(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listAppointments(access(request), storeCode, siteCode, status, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/asns/{asnId}/appointment")
    public AppointmentView upsertAppointment(
            @PathVariable String asnId,
            @RequestBody UpsertAppointmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().upsertAppointment(access(request), asnId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/asns/{asnId}/appointment/manual")
    public AppointmentView submitManualAppointment(
            @PathVariable String asnId,
            @RequestBody UpsertAppointmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().submitManualAppointment(access(request), asnId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/asns/{asnId}/appointment/availability")
    public List<AppointmentAvailabilityView> appointmentAvailability(
            @PathVariable String asnId,
            @RequestBody UpsertAppointmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().listAppointmentAvailability(access(request), asnId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @GetMapping("/asns/{asnId}/appointment/warehouses")
    public List<String> appointmentWarehouses(
            @PathVariable String asnId,
            HttpServletRequest request
    ) {
        try {
            return service().listAppointmentWarehouseFromOptions(access(request), asnId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/appointments/{appointmentId}/run-once")
    public AppointmentView runAppointmentOnce(
            @PathVariable String appointmentId,
            HttpServletRequest request
    ) {
        try {
            return service().runAppointmentOnce(access(request), appointmentId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IllegalStateException exception) {
            throw upstreamFailure(exception);
        }
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public AppointmentView cancelAppointment(
            @PathVariable String appointmentId,
            HttpServletRequest request
    ) {
        try {
            return service().cancelAppointment(access(request), appointmentId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/appointments/{appointmentId}/correction")
    public AppointmentView correctAppointment(
            @PathVariable String appointmentId,
            @RequestBody CorrectAppointmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().correctAppointment(access(request), appointmentId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/appointments/{appointmentId}/noon-calls")
    public List<NoonHttpCallLogView> appointmentNoonCalls(
            @PathVariable String appointmentId,
            HttpServletRequest request
    ) {
        try {
            return service().listAppointmentNoonCalls(access(request), appointmentId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext access(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.OFFICIAL_WAREHOUSE);
    }

    private BusinessAccessContext storeAccess(HttpServletRequest request, String storeCode) {
        return accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, storeCode);
    }

    private LocalDbOfficialWarehouseService service() {
        LocalDbOfficialWarehouseService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓服务未启用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private RuntimeException upstreamFailure(IllegalStateException exception) {
        if (exception instanceof NoonOperationException) {
            return ApiProblemException.fromNoon((NoonOperationException) exception);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
    }
}
