package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLSyntaxErrorException;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerFailureSignatureTest {

    @Test
    void keepsTypesAndSqlCodeWithoutMessages() {
        RuntimeException failure = new RuntimeException(
                "scope=private-store",
                new SQLSyntaxErrorException("secret sql text", "42000", 1064)
        );

        assertThat(ScheduleReconciler.failureSignature(failure))
                .isEqualTo("RuntimeException>SQLSyntaxErrorException[42000:1064]")
                .doesNotContain("private-store", "secret sql text");
    }

    @Test
    void reportsOnlyWhitelistedScheduleFrameWithoutExceptionMessages() {
        RuntimeException failure = new RuntimeException("scope=private-store");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "org.mybatis.spring.SqlSessionTemplate", "selectOne", "secret", 99
                ),
                new StackTraceElement(
                        "com.nuono.next.datapull.schedule.MyBatisScheduleManifestVerifier",
                        "advance", "private-store", 37
                )
        });

        assertThat(ScheduleReconciler.failureLocation(failure))
                .isEqualTo("MyBatisScheduleManifestVerifier#advance:37")
                .doesNotContain("private-store", "secret");
    }
}
