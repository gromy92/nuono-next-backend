package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.util.List;
import org.springframework.util.StringUtils;

/** Opens a fresh pinned session after the read proof and before the first ASN write. */
final class OfficialWarehouseAsnWritePreparation {

    private OfficialWarehouseAsnWritePreparation() {
    }

    static Prepared prepare(
            NoonSessionGateway gateway,
            OfficialWarehouseAsnProductPreflightModule preflight,
            Long ownerUserId,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            List<AsnLineInsertRecord> lineRows
    ) {
        NoonSession readSession = open(gateway, ownerUserId, binding, binding.getPersistedCookie());
        OfficialWarehouseAsnProductPreflightProof proof =
                preflight.freeze(readSession, binding, context, lineRows);
        NoonSession writeSession = open(
                gateway,
                ownerUserId,
                binding,
                firstNonBlank(readSession.exportAuthCookieHeader(), binding.getPersistedCookie())
        );
        return new Prepared(writeSession, proof);
    }

    private static NoonSession open(
            NoonSessionGateway gateway,
            Long ownerUserId,
            NoonSalesReportBinding binding,
            String persistedCookie
    ) {
        return gateway.loginWithPersistedCookiePinnedEgress(
                ownerUserId,
                binding.getNoonUser(),
                persistedCookie,
                binding.getProjectCode(),
                binding.getStoreCode(),
                "fbn.noon.partners",
                443
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : fallback;
    }

    static final class Prepared {
        private final NoonSession writeSession;
        private final OfficialWarehouseAsnProductPreflightProof preflightProof;

        private Prepared(
                NoonSession writeSession,
                OfficialWarehouseAsnProductPreflightProof preflightProof
        ) {
            this.writeSession = writeSession;
            this.preflightProof = preflightProof;
        }

        NoonSession writeSession() {
            return writeSession;
        }

        OfficialWarehouseAsnProductPreflightProof preflightProof() {
            return preflightProof;
        }
    }
}
