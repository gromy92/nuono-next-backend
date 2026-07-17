package com.nuono.next.noon;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@FunctionalInterface
public interface NoonEmailOtpReader {

    String readOtp(String email, String mailAuthCode);

    default MailboxCursor snapshot(String email, String mailAuthCode) {
        throw new UnsupportedOperationException("邮箱 OTP 读取器不支持代次快照。");
    }

    default Optional<OtpCandidate> pollAfter(
            String email,
            String mailAuthCode,
            MailboxCursor cursor,
            Instant notBefore,
            Set<String> excludedMessageKeyHashes
    ) {
        throw new UnsupportedOperationException("邮箱 OTP 读取器不支持代次读取。");
    }

    default void acknowledge(String email, String mailAuthCode, OtpCandidate candidate) {
        throw new UnsupportedOperationException("邮箱 OTP 读取器不支持消费确认。");
    }

    final class MailboxCursor {
        private final long uidValidity;
        private final long lastUid;
        private final Instant capturedAt;

        public MailboxCursor(long uidValidity, long lastUid, Instant capturedAt) {
            this.uidValidity = uidValidity;
            this.lastUid = Math.max(0L, lastUid);
            this.capturedAt = capturedAt;
        }

        public long getUidValidity() {
            return uidValidity;
        }

        public long getLastUid() {
            return lastUid;
        }

        public Instant getCapturedAt() {
            return capturedAt;
        }
    }

    final class OtpCandidate {
        private final String code;
        private final String messageKeyHash;
        private final Instant receivedAt;
        private final long uidValidity;
        private final long uid;

        public OtpCandidate(
                String code,
                String messageKeyHash,
                Instant receivedAt,
                long uidValidity,
                long uid
        ) {
            this.code = code;
            this.messageKeyHash = messageKeyHash;
            this.receivedAt = receivedAt;
            this.uidValidity = uidValidity;
            this.uid = uid;
        }

        public String getCode() {
            return code;
        }

        public String getMessageKeyHash() {
            return messageKeyHash;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public long getUidValidity() {
            return uidValidity;
        }

        public long getUid() {
            return uid;
        }
    }
}
