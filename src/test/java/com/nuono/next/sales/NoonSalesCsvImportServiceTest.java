package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonSalesCsvImportServiceTest {

    @Test
    void emptyReportMarksSiteOffersAsNotListed() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        NoonSalesCsvImportCommand command = new NoonSalesCsvImportCommand(
                307L,
                50001L,
                "STR245027-NSA",
                "SA",
                "empty.csv",
                headerOnlyCsv()
        );

        NoonSalesCsvImportResult result = service.importCsv(command);

        assertEquals("empty", result.getStatus());
        assertEquals(0, result.getTotalRows());
        assertEquals(1, repository.savedBatches.size());
        assertEquals(1, repository.notListedMarks);
        assertEquals(307L, repository.lastOwnerUserId);
        assertEquals("STR245027-NSA", repository.lastStoreCode);
        assertEquals("SA", repository.lastSiteCode);
        assertEquals(307L, repository.lastUpdatedBy);
    }

    private static String headerOnlyCsv() {
        return String.join(",", List.of(
                "Visit_Date",
                "Partner_SKU",
                "Mp_Code",
                "SKU_CONFIG",
                "SKU",
                "Family",
                "Product_Type",
                "Product_Subtype",
                "Brand",
                "Currency_Code",
                "Product_Title",
                "Country_Code",
                "Your_Visitors",
                "Total_Visitors",
                "Gross_Units",
                "Shipped_Units",
                "Cancelled_Units",
                "Revenue_Shipped",
                "Buy_Box_Visitor_Percentage",
                "Conversion_Visitors_Percentage",
                "ASP_shipped_Percentage"
        ));
    }

    private static final class RecordingSalesFactRepository implements SalesFactRepository {
        private final List<SalesImportBatch> savedBatches = new ArrayList<>();
        private int notListedMarks;
        private Long lastOwnerUserId;
        private String lastStoreCode;
        private String lastSiteCode;
        private Long lastUpdatedBy;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            savedBatches.add(batch);
            return 10012L;
        }

        @Override
        public void upsert(DailySalesFact fact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return List.of();
        }

        @Override
        public void markSiteOffersNotListedForEmptyReport(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long updatedBy
        ) {
            notListedMarks++;
            lastOwnerUserId = ownerUserId;
            lastStoreCode = storeCode;
            lastSiteCode = siteCode;
            lastUpdatedBy = updatedBy;
        }
    }
}
