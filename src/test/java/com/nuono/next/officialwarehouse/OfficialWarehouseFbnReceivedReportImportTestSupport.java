package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OfficialWarehouseFbnReceivedReportImportTestSupport {
    private OfficialWarehouseFbnReceivedReportImportTestSupport() {
    }

    static String receivedCsv() {
        return receivedHeader()
                + "PAPERSAYSB105N1,Z0B8C025C4C884FD10BE6Z-1,,6287053004607,standard,0.01,Papersay,"
                + "\"A4 file bag\",A05508658PN,-,RUH01S,sa,1,1,0,0,-,2026-06-11,2026-06-11,2026-06-13\n"
                + "PAPERSAYSB042,Z9DDECF61092EFCE742E9Z-1,,6287053004508,standard,0.02,Papersay,"
                + "Tape,A05508658PN,-,RUH01S,sa,3,2,0,1,missing,2026-06-11,2026-06-11,2026-06-13\n";
    }

    static String receivedHeader() {
        return "partner_sku,sku,po_nr,pbarcode_canonical,storage_type_code,volume,brand,product_title,asn,"
                + "partner_warehouse,noon_warehouse,country_code,qty_expected,received_qty,qc_failed_qty,"
                + "unidentified_qty,qc_failed_reason,asn_created_at,asn_schedule_date,asn_completed_at\n";
    }

    static String minimalReceivedHeader() {
        return "partner_sku,sku,asn,qty_expected,received_qty,qc_failed_qty,unidentified_qty,"
                + "asn_schedule_date\n";
    }

    static String minimalReceivedRow(
            String partnerSku,
            String noonSku,
            String asn,
            String qtyExpected,
            String receivedQty,
            String qcFailedQty,
            String unidentifiedQty,
            String asnScheduleDate
    ) {
        return String.join(",", partnerSku, noonSku, asn, qtyExpected, receivedQty,
                qcFailedQty, unidentifiedQty, asnScheduleDate) + "\n";
    }

    static InventorySyncScopeRecord scope() {
        InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        scope.ownerUserId = 307L;
        scope.logicalStoreId = 7001L;
        scope.storeCode = "STR108065-NSA";
        scope.siteCode = "SA";
        scope.projectCode = "PRJ108065";
        scope.partnerId = "108065";
        return scope;
    }

    static FbnReceivedImportCommand command() {
        FbnReceivedImportCommand command = new FbnReceivedImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        return command;
    }

    static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/warehouse/official-warehouse-stock"))
                .build();
    }

    static final class FakeFbnExportProvider extends OfficialWarehouseFbnExportProvider {
        final List<String> statusRequests = new ArrayList<>();
        final List<String> downloadRequests = new ArrayList<>();
        ExportStatus status;
        byte[] downloadedBytes;

        FakeFbnExportProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
            statusRequests.add(request.ownerUserId + ":" + request.storeCode + ":"
                    + request.siteCode + ":" + exportCode + ":" + log);
            return status;
        }

        @Override
        public byte[] download(PullRequest request, String downloadUrl) {
            downloadRequests.add(
                    request.ownerUserId + ":" + request.storeCode + ":" + request.siteCode
            );
            return downloadedBytes;
        }
    }
}
