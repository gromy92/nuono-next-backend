package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noon.NoonResponseClassifier;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.web.ApiProblemException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfficialWarehouseNoonProblemTranslatorTest {

    @Test
    void enrichesPbarcodeFailureWithLocalProductAndPartialSuccessContext() {
        NoonHttpException providerResponse = new NoonHttpException(
                400,
                "{\"error\":\"psku_codes ['afbb2138c32bb212a0eab446b986b2aa'] invalid or not mapped to pbarcode\"}",
                "/asn/lines/create-batch"
        );
        NoonOperationException operationException = new NoonOperationException(
                new NoonResponseClassifier().classify("CREATE_ASN_LINES", providerResponse),
                providerResponse
        );
        AsnLineInsertRecord line = new AsnLineInsertRecord();
        line.partnerSku = "SGGRB261";
        line.pskuCode = "afbb2138c32bb212a0eab446b986b2aa";
        line.noonSku = "N70000001A";
        line.titleCache = "测试商品";

        ApiProblemException problem = OfficialWarehouseNoonProblemTranslator.createAsnProblem(
                operationException,
                501277L,
                "OWA-501277",
                "A05726515PN",
                List.of(line)
        );

        assertThat(problem.getStatus().value()).isEqualTo(422);
        assertThat(problem.getCode()).isEqualTo("NOON_PBARCODE_UNMAPPED");
        assertThat(problem.isPartialSuccess()).isTrue();
        assertThat(problem.isRetryable()).isFalse();
        assertThat(problem.getReference()).isEqualTo("A05726515PN");
        assertThat(problem.getMessage())
                .contains("Noon 已创建 ASN A05726515PN")
                .contains("请勿重复创建")
                .contains("SGGRB261")
                .contains("afbb2138c32bb212a0eab446b986b2aa")
                .doesNotContain("invalid or not mapped");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> affectedProducts =
                (List<Map<String, Object>>) problem.getDetails().get("affectedProducts");
        assertThat(affectedProducts).singleElement().satisfies(product -> {
            assertThat(product.get("partnerSku")).isEqualTo("SGGRB261");
            assertThat(product.get("pskuCode")).isEqualTo("afbb2138c32bb212a0eab446b986b2aa");
        });
    }
}
