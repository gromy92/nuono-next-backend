package com.nuono.next.noon;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonEmailOtpReader.OtpCandidate;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

abstract class AbstractNoonSessionGatewayAuthRecoveryTestSupport {
    protected static final Instant ATTEMPTED_AT =
            Instant.parse("2026-07-16T00:00:00Z");
    protected static final String TARGET_PROJECT = "PRJ7001";

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected NoonSessionGatewayAuthRecoveryGateway recoveryGateway(
            NoonSessionGateway gateway
    ) {
        MutableClock clock = new MutableClock(ATTEMPTED_AT);
        return recoveryGateway(
                gateway,
                immediateOtpReader(),
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                clock,
                clock::advanceMillis
        );
    }

    protected NoonSessionGatewayAuthRecoveryGateway recoveryGateway(
            NoonSessionGateway gateway,
            NoonEmailOtpReader otpReader,
            Duration pollInterval,
            Duration pollTimeout,
            Clock clock,
            NoonSessionGatewayAuthRecoveryGateway.Sleeper sleeper
    ) {
        gateway.setConfiguredMerchantEmailOtpCredential(
                "merchant@example.com",
                "imap-secret"
        );
        return new NoonSessionGatewayAuthRecoveryGateway(
                gateway,
                otpReader,
                pollInterval,
                pollTimeout,
                clock,
                sleeper
        );
    }

    protected NoonSessionGateway identityGateway(RecoveryServer server) {
        NoonSessionGateway gateway = new NoonSessionGateway(
                objectMapper,
                mock(StoreSyncMapper.class),
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                false,
                "",
                server.url("/whoami"),
                server.url("/lookup"),
                server.url("/pkce"),
                server.url("/generate"),
                server.url("/validate"),
                server.url("/projects"),
                server.url("/session-create"),
                false,
                "HTTP",
                "",
                0,
                ""
        );
        gateway.setCatalogCapabilityProbeUrl(server.catalogUrl("/catalog"));
        gateway.setCatalogSessionBootstrapUrl(server.catalogUrl("/catalog-bootstrap"));
        return gateway;
    }

    protected NoonEmailOtpReader immediateOtpReader() {
        return new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError(
                        "central recovery must use generation-aware mailbox reads"
                );
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                if (excludedMessageKeyHashes.contains("message-key-hash")) {
                    return Optional.empty();
                }
                return Optional.of(new OtpCandidate(
                        "654321",
                        "message-key-hash",
                        ATTEMPTED_AT.plusSeconds(1),
                        7L,
                        101L
                ));
            }

            @Override
            public void acknowledge(
                    String email,
                    String mailAuthCode,
                    OtpCandidate candidate
            ) {
                // no-op
            }
        };
    }

    protected OtpCandidate otpCandidate(String code, String messageKeyHash, long uid) {
        return new OtpCandidate(
                code,
                messageKeyHash,
                ATTEMPTED_AT.plusSeconds(1),
                7L,
                uid
        );
    }

    protected NoonAuthRecoveryAttemptCommand command() {
        return command(
                List.of(new NoonAuthRecoveryProjectTarget(
                        307L,
                        TARGET_PROJECT,
                        "STR7001-NAE",
                        0L
                )),
                () -> true
        );
    }

    protected NoonAuthRecoveryAttemptCommand command(
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptCommand.LeaseHeartbeat leaseHeartbeat
    ) {
        return command(targets, leaseHeartbeat, () -> true);
    }

    protected NoonAuthRecoveryAttemptCommand command(
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptCommand.LeaseHeartbeat leaseHeartbeat,
            NoonAuthRecoveryAttemptCommand.BeforeOtpSend beforeOtpSend
    ) {
        return new NoonAuthRecoveryAttemptCommand(
                9001L,
                1,
                ATTEMPTED_AT,
                Set.of(),
                targets,
                leaseHeartbeat,
                beforeOtpSend
        );
    }

    protected List<NoonAuthRecoveryProjectTarget> projectTargets(int count) {
        List<NoonAuthRecoveryProjectTarget> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String suffix = String.format("%04d", 7001 + index);
            targets.add(new NoonAuthRecoveryProjectTarget(
                    307L + index,
                    "PRJ" + suffix,
                    "STR" + suffix + "-NAE",
                    0L
            ));
        }
        return List.copyOf(targets);
    }

    protected List<String> projectCodes(List<NoonAuthRecoveryProjectTarget> targets) {
        List<String> projectCodes = new ArrayList<>();
        for (NoonAuthRecoveryProjectTarget target : targets) {
            projectCodes.add(target.getProjectCode());
        }
        return List.copyOf(projectCodes);
    }

    protected boolean containsProviderSecret(String value) {
        return value != null && value.contains("provider-secret");
    }

    protected static final class SequencedOtpReader implements NoonEmailOtpReader {
        private final List<OtpCandidate> candidates;
        private final List<String> acknowledgedMessageKeyHashes = new ArrayList<>();

        protected SequencedOtpReader(List<OtpCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public String readOtp(String email, String mailAuthCode) {
            throw new AssertionError(
                    "central recovery must use generation-aware mailbox reads"
            );
        }

        @Override
        public MailboxCursor snapshot(String email, String mailAuthCode) {
            return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
        }

        @Override
        public Optional<OtpCandidate> pollAfter(
                String email,
                String mailAuthCode,
                MailboxCursor cursor,
                Instant notBefore,
                Set<String> excludedMessageKeyHashes
        ) {
            return candidates.stream()
                    .filter(candidate -> !excludedMessageKeyHashes.contains(
                            candidate.getMessageKeyHash()
                    ))
                    .findFirst();
        }

        @Override
        public void acknowledge(
                String email,
                String mailAuthCode,
                OtpCandidate candidate
        ) {
            acknowledgedMessageKeyHashes.add(candidate.getMessageKeyHash());
        }

        protected List<String> acknowledgedMessageKeyHashes() {
            return List.copyOf(acknowledgedMessageKeyHashes);
        }
    }

    protected static final class MutableClock extends Clock {
        private Instant current;

        protected MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        protected void advanceMillis(long millis) {
            current = current.plusMillis(millis);
        }
    }
}
