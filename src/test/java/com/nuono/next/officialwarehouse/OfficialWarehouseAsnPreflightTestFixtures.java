package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnLineCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ProductCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

final class OfficialWarehouseAsnPreflightTestFixtures {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OfficialWarehouseAsnPreflightTestFixtures() {
    }

    static ProductCandidateRecord candidate(String partnerSku, String psku, String noonSku, long id) {
        ProductCandidateRecord row = new ProductCandidateRecord();
        row.ownerUserId = 307L;
        row.logicalStoreId = 108065L;
        row.logicalStoreSiteId = 1080651L;
        row.storeCode = "STR108065-NSA";
        row.siteCode = "SA";
        row.productMasterId = id + 1000;
        row.productVariantId = id;
        row.productSiteOfferId = id + 2000;
        row.partnerSku = partnerSku;
        row.pskuCode = psku;
        row.noonSku = noonSku;
        row.titleCache = partnerSku;
        row.productLengthCm = BigDecimal.TEN;
        row.productWidthCm = BigDecimal.TEN;
        row.productHeightCm = BigDecimal.TEN;
        return row;
    }

    static ShippingBatchSourceAllocationRecord allocation(int quantity) {
        return allocation("SGGRB329", "SGGRB329", quantity);
    }

    static ShippingBatchSourceAllocationRecord allocation(
            String partnerSku,
            String sourceBarcode,
            int quantity
    ) {
        ShippingBatchSourceAllocationRecord row = new ShippingBatchSourceAllocationRecord();
        row.inTransitBatchId = 53023L;
        row.shippingBatchNo = "XGGEKSA04075";
        row.inTransitGoodsLineId = 54282L;
        row.partnerSku = partnerSku;
        row.sourceBarcode = sourceBarcode;
        row.quantity = quantity;
        return row;
    }

    static CreateAsnCommand command(CreateAsnLineCommand... lines) {
        CreateAsnCommand command = new CreateAsnCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.lines = List.of(lines);
        return command;
    }

    static CreateAsnLineCommand line(String partnerSku, int quantity) {
        CreateAsnLineCommand line = new CreateAsnLineCommand();
        line.partnerSku = partnerSku;
        line.quantity = quantity;
        return line;
    }

    static ObjectNode offerPage(String partnerSku, String psku, String... pbarcodes) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.put("total", 1);
        ObjectNode hit = data.putArray("hits").addObject()
                .put("partner_sku", partnerSku).put("psku_code", psku);
        for (String pbarcode : pbarcodes) hit.withArray("partner_barcodes").add(pbarcode);
        return root;
    }

    static ObjectNode createResponse() {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putObject("data").put("asn_nr", "A05834999PN")
                .put("id_partner_asn", 9001).put("total_qty", 5);
        return root;
    }

    static ObjectNode routingResponse() {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putArray("data").addObject().put("partner_code", "RUH01S").put("code", "W00105371A");
        return root;
    }

    static BusinessAccessContext access() {
        return BusinessAccessContext.builder().sessionUserId(901L).businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS).storeCodes(Set.of("STR108065-NSA")).build();
    }

    static StoreSiteRecord site() {
        StoreSiteRecord row = new StoreSiteRecord();
        row.ownerUserId = 307L; row.logicalStoreId = 108065L; row.storeCode = "STR108065-NSA";
        row.storeName = "Canman"; row.siteCode = "SA"; row.projectCode = "PRJ108065";
        return row;
    }

    static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(307L, 108065L, "PRJ108065", "STR108065-NSA", "SA",
                "108065", "merchant@example.com", "persisted-cookie");
    }

    static AsnRecord asnRecord() {
        AsnRecord row = new AsnRecord();
        row.id = 500001L; row.ownerUserId = 307L; row.logicalStoreId = 108065L;
        row.storeCode = "STR108065-NSA"; row.siteCode = "SA"; row.localAsnNo = "OWA-500001";
        row.noonAsnNr = "A05834999PN"; row.status = "LINES_CREATED"; row.totalQuantity = 5;
        return row;
    }
}
