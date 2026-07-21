package com.nuono.next.postsaleprofit;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
final class PostSaleProfitHttpSupport {
    private final BusinessAccessResolver businessAccessResolver;

    PostSaleProfitHttpSupport(BusinessAccessResolver businessAccessResolver) {
        this.businessAccessResolver = businessAccessResolver;
    }

    DatedStoreScope validateDatedStoreScope(
            String storeCode,
            String siteCode,
            String dateFrom,
            String dateTo
    ) {
        StoreScope storeScope = validateStoreScope(storeCode, siteCode);
        if (!StringUtils.hasText(dateFrom) || !StringUtils.hasText(dateTo)) {
            throw badRequest("storeCode, siteCode, dateFrom and dateTo are required.");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(dateFrom.trim());
            to = LocalDate.parse(dateTo.trim());
        } catch (RuntimeException exception) {
            throw badRequest("dateFrom and dateTo must be ISO dates.", exception);
        }
        if (to.isBefore(from)) {
            throw badRequest("dateTo must be on or after dateFrom.");
        }
        return new DatedStoreScope(storeScope.storeCode(), storeScope.siteCode(), from, to);
    }

    StoreScope validateStoreScope(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw badRequest("storeCode and siteCode are required.");
        }
        return new StoreScope(storeCode.trim(), siteCode.trim());
    }

    Long requireOwnerUserId(HttpServletRequest request, String storeCode) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    LocalDate parseRequiredDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(fieldName + " is required.");
        }
        return parseDate(value, fieldName);
    }

    LocalDate parseOptionalDate(String value, String fieldName) {
        return StringUtils.hasText(value) ? parseDate(value, fieldName) : null;
    }

    String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw badRequest(fieldName + " must be ISO date.", exception);
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException badRequest(String reason, RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason, cause);
    }

    static class StoreScope {
        private final String storeCode;
        private final String siteCode;

        StoreScope(String storeCode, String siteCode) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }

        String storeCode() {
            return storeCode;
        }

        String siteCode() {
            return siteCode;
        }
    }

    static final class DatedStoreScope extends StoreScope {
        private final LocalDate dateFrom;
        private final LocalDate dateTo;

        DatedStoreScope(String storeCode, String siteCode, LocalDate dateFrom, LocalDate dateTo) {
            super(storeCode, siteCode);
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
        }

        LocalDate dateFrom() {
            return dateFrom;
        }

        LocalDate dateTo() {
            return dateTo;
        }
    }
}
