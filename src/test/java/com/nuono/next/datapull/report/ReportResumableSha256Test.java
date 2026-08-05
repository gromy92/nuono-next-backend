package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class ReportResumableSha256Test {

    @Test
    void persistedStateMatchesJdkDigestAfterResume() throws Exception {
        byte[] first = "first durable report chunk".getBytes(StandardCharsets.UTF_8);
        byte[] second = "remaining suffix".getBytes(StandardCharsets.UTF_8);
        ReportResumableSha256 digest = new ReportResumableSha256();
        digest.update(first);

        ReportResumableSha256 resumed = ReportResumableSha256.resume(digest.snapshot());
        resumed.update(second);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update(first);
        expected.update(second);

        assertEquals(first.length + second.length, resumed.byteCount());
        assertEquals(hex(expected.digest()), resumed.finishHex());
    }

    private String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
