package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImapNoonEmailOtpReaderContractTest {

    @Test
    void readerShouldNotReuseOldSeenOtpMessages() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/noon/ImapNoonEmailOtpReader.java"
        ));

        assertTrue(source.contains("profile.add(FetchProfile.Item.FLAGS)"));
        assertTrue(source.contains("message.isSet(Flags.Flag.SEEN)"));
        assertTrue(source.contains("requestedAt.minus(DELIVERY_CLOCK_SKEW)"));
        assertTrue(source.contains("!receivedDate.toInstant().isBefore(threshold)"));
        assertTrue(source.contains("message.setFlag(Flags.Flag.SEEN, true)"));
    }
}
