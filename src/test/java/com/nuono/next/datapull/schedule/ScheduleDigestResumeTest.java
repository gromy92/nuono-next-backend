package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class ScheduleDigestResumeTest {

    @Test
    void resumableSha256MatchesJdkAcrossAStoredBoundary() throws Exception {
        byte[] first = "first bounded page".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second bounded page".getBytes(StandardCharsets.UTF_8);
        ResumableSha256 digest = new ResumableSha256();
        digest.update(first);

        ResumableSha256 resumed = ResumableSha256.resume(digest.snapshot());
        resumed.update(second);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update(first);
        expected.update(second);

        assertEquals(hex(expected.digest()), resumed.finishHex());
    }

    @Test
    void sourcePassDigestBindsNativeCursorScopeAndImmutablePayload() {
        String payload = "a".repeat(64);
        ScheduleSourceOrderedDigest first = ScheduleSourceOrderedDigest.initial()
                .append("NOON1:1:2:3:4:5", "scope-a", payload)
                .append("NOON1:1:2:3:4:6", "scope-b", payload);
        ScheduleSourceOrderedDigest same = ScheduleSourceOrderedDigest.initial()
                .append("NOON1:1:2:3:4:5", "scope-a", payload)
                .append("NOON1:1:2:3:4:6", "scope-b", payload);
        ScheduleSourceOrderedDigest drift = ScheduleSourceOrderedDigest.initial()
                .append("NOON1:1:2:3:4:5", "scope-a", payload)
                .append("NOON1:1:2:3:4:6", "scope-b", "b".repeat(64));

        assertEquals(first.snapshot(), same.snapshot());
        assertNotEquals(first.snapshot(), drift.snapshot());
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
