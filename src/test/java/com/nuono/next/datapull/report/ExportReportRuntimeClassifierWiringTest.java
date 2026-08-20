package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.noonpull.NoonOrderReportAdapter;
import com.nuono.next.noonpull.NoonOrderReportRowClassifier;
import com.nuono.next.noonpull.NoonSalesReportAdapter;
import com.nuono.next.noonpull.NoonSalesReportRowClassifier;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportRowClassifier;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExportReportRuntimeClassifierWiringTest {
    @Test
    void runtimeJobsDependOnPureRowClassifiersInsteadOfLegacyDirectWriteAdapters() {
        assertClassifierBoundary(
                "dp01ReportJob",
                NoonSalesReportRowClassifier.class,
                NoonSalesReportAdapter.class
        );
        assertClassifierBoundary(
                "dp02ReportJob",
                NoonOrderReportRowClassifier.class,
                NoonOrderReportAdapter.class
        );
        assertClassifierBoundary(
                "dp03ReportJob",
                NoonFinanceTransactionReportRowClassifier.class,
                NoonFinanceTransactionReportAdapter.class
        );
    }

    @Test
    void legacyAdaptersDoNotExposeRuntimeStageForwarders() {
        for (Class<?> adapter : Arrays.asList(
                NoonSalesReportAdapter.class,
                NoonOrderReportAdapter.class,
                NoonFinanceTransactionReportAdapter.class
        )) {
            assertThat(Arrays.stream(adapter.getDeclaredMethods()).map(Method::getName))
                    .doesNotContain("requireStageHeader", "classifyStageRows", "stageIdentity");
        }
    }

    private void assertClassifierBoundary(
            String factoryMethod,
            Class<?> classifier,
            Class<?> legacyAdapter
    ) {
        Method method = Arrays.stream(ExportReportRuntimeConfiguration.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(factoryMethod))
                .findFirst()
                .orElseThrow();
        assertThat(method.getParameterTypes())
                .contains(classifier)
                .doesNotContain(legacyAdapter);
    }
}
