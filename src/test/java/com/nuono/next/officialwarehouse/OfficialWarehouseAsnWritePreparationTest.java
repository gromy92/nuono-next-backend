package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAsnPreflightTestFixtures.binding;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAsnPreflightTestFixtures.offerPage;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAsnPreflightTestFixtures.testNoonSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OfficialWarehouseAsnWritePreparationTest {

    private NoonSessionGateway gateway;
    private OfficialWarehouseNoonInboundClient inboundClient;
    private OfficialWarehouseAsnProductPreflightModule preflight;
    private NoonSalesReportBinding binding;
    private NoonSession readSession;
    private NoonSession writeSession;
    private String latestCookie;

    @BeforeEach
    void setUp() {
        gateway = mock(NoonSessionGateway.class);
        inboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        preflight = new OfficialWarehouseAsnProductPreflightModule(inboundClient);
        binding = binding();
        readSession = testNoonSession("_npsid=read-session");
        writeSession = testNoonSession("_npsid=write-session");
        latestCookie = readSession.exportAuthCookieHeader();
        when(gateway.loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", "persisted-cookie", "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443)).thenReturn(readSession);
        when(gateway.loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", latestCookie, "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443)).thenReturn(writeSession);
        when(inboundClient.searchProductOffersPage(eq(readSession), any(), any(), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-329", "PB-329"));
    }

    @Test
    void opensFreshWriteSessionWithLatestCookieAfterReadProof() {
        NoonCallContext context = NoonCallContext.asn(500001L, "OWA-500001");

        OfficialWarehouseAsnWritePreparation.Prepared prepared =
                OfficialWarehouseAsnWritePreparation.prepare(
                        gateway, preflight, 307L, binding, context, List.of(line()));

        assertThat(prepared.writeSession()).isSameAs(writeSession);
        assertThat(prepared.preflightProof().totalQuantity()).isEqualTo(5);
        assertThat(latestCookie).contains("_npsid=read-session");
        InOrder order = inOrder(gateway, inboundClient);
        order.verify(gateway).loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", "persisted-cookie", "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443);
        order.verify(inboundClient).searchProductOffersPage(eq(readSession), any(), any(), any());
        order.verify(gateway).loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", latestCookie, "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443);
    }

    @Test
    void freshWriteSessionFailureStopsPreparationAfterProof() {
        when(gateway.loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", latestCookie, "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443))
                .thenThrow(new IllegalStateException("fresh egress unavailable"));

        assertThatThrownBy(() -> OfficialWarehouseAsnWritePreparation.prepare(
                gateway, preflight, 307L, binding,
                NoonCallContext.asn(500001L, "OWA-500001"), List.of(line())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fresh egress unavailable");

        verify(inboundClient).searchProductOffersPage(eq(readSession), any(), any(), any());
    }

    @Test
    void servicePreparesFreshSessionBeforePersistingLocalAsn() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(source.indexOf("OfficialWarehouseAsnWritePreparation.prepare("))
                .isLessThan(source.indexOf("mapper.insertAsn(asnRow)"));
    }

    private AsnLineInsertRecord line() {
        AsnLineInsertRecord line = new AsnLineInsertRecord();
        line.partnerSku = "SGGRB329";
        line.pskuCode = "PSKU-329";
        line.quantity = 5;
        return line;
    }
}
