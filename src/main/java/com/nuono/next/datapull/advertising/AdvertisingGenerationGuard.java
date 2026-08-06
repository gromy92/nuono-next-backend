package com.nuono.next.datapull.advertising;

import java.util.Objects;
import java.util.regex.Pattern;

/** Central fail-closed contract for raw proof, durable cursor, and current-head CAS. */
final class AdvertisingGenerationGuard {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    boolean isCurrentTask(AdvertisingTaskFenceRow task, AdvertisingApplyCommand command) {
        return task != null
                && Objects.equals(task.getTaskId(), command.getTaskId())
                && "DP06".equals(task.getOperationCode())
                && Objects.equals(task.getOwnerUserId(), command.getOwnerUserId())
                && Objects.equals(task.getProjectCode(), command.getProjectCode())
                && Objects.equals(task.getStoreCode(), command.getStoreCode())
                && Objects.equals(task.getSiteCode(), command.getSiteCode())
                && Objects.equals(task.getBusinessWindowKey(), command.getBusinessWindowKey())
                && Objects.equals(task.getScheduleSlot(), command.getScheduleSlot())
                && Objects.equals(task.getFenceEpoch(), command.getFenceEpoch())
                && "RUNNING".equals(task.getState())
                && Objects.equals(task.getLeaseOwner(), command.getLeaseOwner())
                && Boolean.TRUE.equals(task.getLeaseValid());
    }

    void validateManifest(
            AdvertisingApplyCommand command,
            AdvertisingStageManifestRow manifest
    ) {
        int expectedLastPage = command.getLastPage();
        long declaredCampaigns = command.getAuthority().getDeclaredCampaignCount();
        if (manifest == null
                || !Objects.equals(manifest.getTaskId(), command.getTaskId())
                || manifest.getActiveFenceEpoch() == null
                || manifest.getActiveFenceEpoch() < 1L
                || manifest.getActiveFenceEpoch() > command.getFenceEpoch()
                || manifest.getPoisonCode() != null
                || !"TWO_PASS_OBSERVATION".equals(manifest.getAuthorityKind())
                || !Objects.equals(manifest.getAuthorityTokenSha256(),
                        command.getAuthority().getGenerationTokenSha256())
                || manifest.getSnapshotAsOfUtc() != null
                || !Objects.equals(manifest.getDeclaredCampaignCount(), declaredCampaigns)
                || !Objects.equals(manifest.getDeclaredTotalPages(), expectedLastPage)
                || !Objects.equals(manifest.getKnownLastPage(), expectedLastPage)
                || !Objects.equals(manifest.getPageCount(), (long) expectedLastPage)
                || !Objects.equals(manifest.getFirstPage(), 1)
                || !Objects.equals(manifest.getLastPage(), expectedLastPage)
                || invalidCount(manifest.getCampaignItemCount())
                || invalidCount(manifest.getCampaignSourceItemCount())
                || invalidCount(manifest.getCampaignBusinessSkippedItemCount())
                || invalidCount(manifest.getStagedItemCount())
                || invalidCount(manifest.getSourceItemCount())
                || invalidCount(manifest.getBusinessSkippedItemCount())
                || manifest.getStagedItemCount() < manifest.getCampaignItemCount()
                || manifest.getStagedItemCount() - manifest.getCampaignItemCount()
                        < command.getActiveCampaigns().size()) {
            throw new IllegalStateException("advertising raw stage manifest is invalid");
        }
        long accounted;
        long campaignAccounted;
        try {
            accounted = Math.addExact(
                    manifest.getStagedItemCount(),
                    manifest.getBusinessSkippedItemCount()
            );
            campaignAccounted = Math.addExact(
                    manifest.getCampaignItemCount(),
                    manifest.getCampaignBusinessSkippedItemCount()
            );
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("advertising raw stage accounting overflow", overflow);
        }
        if (accounted != manifest.getSourceItemCount()
                || campaignAccounted != manifest.getCampaignSourceItemCount()
                || campaignAccounted != declaredCampaigns) {
            throw new IllegalStateException("advertising raw stage accounting is invalid");
        }
    }

    void requireSameGeneration(
            AdvertisingGenerationRow row,
            AdvertisingApplyCommand command
    ) {
        int expectedLastPage = command.getLastPage();
        long declaredCampaigns = command.getAuthority().getDeclaredCampaignCount();
        if (row == null || !Objects.equals(row.getTaskId(), command.getTaskId())
                || !Objects.equals(row.getOwnerUserId(), command.getOwnerUserId())
                || !Objects.equals(row.getProjectCode(), command.getProjectCode())
                || !Objects.equals(row.getStoreCode(), command.getStoreCode())
                || !Objects.equals(row.getSiteCode(), command.getSiteCode())
                || !Objects.equals(row.getReportDate(), command.getReportDate())
                || !Objects.equals(row.getScheduleSlot(), command.getScheduleSlot())
                || !Objects.equals(row.getBusinessWindowKey(), command.getBusinessWindowKey())
                || !Objects.equals(row.getAuthorityTokenSha256(),
                        command.getAuthority().getGenerationTokenSha256())
                || !Objects.equals(row.getActiveCampaignDigestSha256(),
                        AdvertisingDigestChain.activeCampaignDigest(command))
                || row.getProviderAsOfUtc() != null
                || !Objects.equals(row.getDeclaredCampaignCount(), declaredCampaigns)
                || !Objects.equals(row.getActiveCampaignCount(),
                        command.getActiveCampaigns().size())
                || !Objects.equals(row.getCampaignPageCount(),
                        command.getCampaignPageCount())
                || !Objects.equals(row.getLastPage(), expectedLastPage)
                || invalidGenerationCounts(row)
                || row.getBatchId() == null || row.getBatchId() < 1L
                || !SHA256.matcher(nullToEmpty(row.getDigestChainSha256())).matches()) {
            throw new IllegalStateException("advertising generation identity drift");
        }
        long stagedCampaigns = row.getStagedCampaignItemCount();
        boolean campaignIdsValid = stagedCampaigns == 0L
                ? row.getCampaignIdStart() == null
                : row.getCampaignIdStart() != null && row.getCampaignIdStart() > 0L;
        long queryCapacity = Math.subtractExact(
                Math.subtractExact(row.getStagedItemCount(), stagedCampaigns),
                row.getActiveCampaignCount()
        );
        boolean queryIdsValid = queryCapacity == 0L
                ? row.getQueryIdStart() == null
                : row.getQueryIdStart() != null && row.getQueryIdStart() > 0L;
        if (!campaignIdsValid || !queryIdsValid) {
            throw new IllegalStateException("advertising generation ID ranges are invalid");
        }
    }

    void requireCompleteAccounting(AdvertisingGenerationRow row) {
        long accounted;
        long campaignAccounted;
        long campaignSource;
        long querySource;
        long queryAccounted;
        try {
            long accepted = Math.addExact(row.getCampaignFactCount(), row.getQueryFactCount());
            accounted = Math.addExact(
                    Math.addExact(accepted, row.getIdentitySkippedItemCount()),
                    row.getQueryPageProofCount()
            );
            campaignAccounted = Math.addExact(
                    row.getCampaignFactCount(),
                    row.getCampaignIdentitySkippedItemCount()
            );
            campaignSource = Math.addExact(
                    row.getStagedCampaignItemCount(),
                    row.getCampaignBusinessSkippedItemCount()
            );
            querySource = Math.subtractExact(Math.subtractExact(
                    row.getSourceItemCount(), row.getActiveCampaignCount()
            ), row.getDeclaredCampaignCount());
            queryAccounted = Math.addExact(row.getQueryFactCount(), Math.addExact(
                    Math.subtractExact(row.getIdentitySkippedItemCount(),
                            row.getCampaignIdentitySkippedItemCount()),
                    Math.subtractExact(row.getBusinessSkippedItemCount(),
                            row.getCampaignBusinessSkippedItemCount())
            ));
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("advertising generation accounting overflow", overflow);
        }
        if (!Objects.equals(row.getProcessedItemCount(), row.getStagedItemCount())
                || accounted != row.getStagedItemCount()
                || campaignAccounted != row.getStagedCampaignItemCount()
                || campaignSource != row.getDeclaredCampaignCount()
                || querySource != queryAccounted
                || !Objects.equals(row.getQueryPageProofCount(), row.getActiveCampaignCount())) {
            throw new IllegalStateException("advertising generation is not completely prepared");
        }
    }

    boolean isNewer(AdvertisingGenerationHeadRow head, AdvertisingApplyCommand command) {
        if (head == null) return false;
        int slotOrder = head.getScheduleSlot().compareTo(command.getScheduleSlot());
        return slotOrder > 0
                || (slotOrder == 0 && head.getTaskId() > command.getTaskId());
    }

    void requireWinningHead(
            AdvertisingGenerationHeadRow head,
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation
    ) {
        if (head == null
                || !Objects.equals(head.getOwnerUserId(), command.getOwnerUserId())
                || !Objects.equals(head.getProjectCode(), command.getProjectCode())
                || !Objects.equals(head.getStoreCode(), command.getStoreCode())
                || !Objects.equals(head.getSiteCode(), command.getSiteCode())
                || !Objects.equals(head.getReportDate(), command.getReportDate())
                || !Objects.equals(head.getTaskId(), command.getTaskId())
                || !Objects.equals(head.getBatchId(), generation.getBatchId())
                || !Objects.equals(head.getScheduleSlot(), command.getScheduleSlot())) {
            throw new IllegalStateException("advertising current head rejected the generation");
        }
    }

    private boolean invalidGenerationCounts(AdvertisingGenerationRow row) {
        if (invalidCount(row.getStagedCampaignItemCount())
                || invalidCount(row.getCampaignBusinessSkippedItemCount())
                || invalidCount(row.getStagedItemCount()) || invalidCount(row.getSourceItemCount())
                || invalidCount(row.getBusinessSkippedItemCount())
                || row.getCampaignPageCount() == null || row.getCampaignPageCount() < 1
                || !Objects.equals(row.getLastPage(), Math.addExact(
                        row.getCampaignPageCount(), row.getActiveCampaignCount()
                ))
                || row.getCursorPageNo() == null || row.getCursorPageNo() < 0
                || row.getCursorItemOrdinal() == null || row.getCursorItemOrdinal() < -1
                || invalidCount(row.getProcessedItemCount())
                || invalidCount(row.getCampaignFactCount())
                || invalidCount(row.getQueryFactCount())
                || invalidCount(row.getIdentitySkippedItemCount())
                || invalidCount(row.getCampaignIdentitySkippedItemCount())
                || row.getCampaignIdentitySkippedItemCount()
                        > row.getIdentitySkippedItemCount()
                || row.getQueryPageProofCount() == null || row.getQueryPageProofCount() < 0
                || row.getQueryPageProofCount() > row.getActiveCampaignCount()
                || row.getMatchedActiveCampaignCount() == null
                || row.getMatchedActiveCampaignCount() < 0
                || row.getProcessedItemCount() > row.getStagedItemCount()
                || row.getStagedCampaignItemCount() > row.getStagedItemCount()
                || row.getCampaignFactCount() > row.getStagedCampaignItemCount()
                || row.getCampaignIdentitySkippedItemCount() > row.getStagedCampaignItemCount()
                || row.getCampaignBusinessSkippedItemCount() > row.getDeclaredCampaignCount()
                || row.getCampaignBusinessSkippedItemCount() > row.getBusinessSkippedItemCount()
                || row.getMatchedActiveCampaignCount() > row.getActiveCampaignCount()) {
            return true;
        }
        try {
            long source = Math.addExact(
                    row.getStagedItemCount(), row.getBusinessSkippedItemCount()
            );
            long processed = Math.addExact(
                    Math.addExact(
                            Math.addExact(row.getCampaignFactCount(), row.getQueryFactCount()),
                            row.getIdentitySkippedItemCount()
                    ),
                    row.getQueryPageProofCount()
            );
            long campaigns = Math.addExact(
                    row.getCampaignFactCount(), row.getCampaignIdentitySkippedItemCount()
            );
            long campaignSource = Math.addExact(
                    row.getStagedCampaignItemCount(), row.getCampaignBusinessSkippedItemCount()
            );
            long querySource = Math.subtractExact(Math.subtractExact(
                    row.getSourceItemCount(), row.getActiveCampaignCount()
            ), row.getDeclaredCampaignCount());
            long queryAccounted = Math.addExact(row.getQueryFactCount(), Math.addExact(
                    Math.subtractExact(row.getIdentitySkippedItemCount(),
                            row.getCampaignIdentitySkippedItemCount()),
                    Math.subtractExact(row.getBusinessSkippedItemCount(),
                            row.getCampaignBusinessSkippedItemCount())
            ));
            return source != row.getSourceItemCount()
                    || processed != row.getProcessedItemCount()
                    || campaigns > row.getStagedCampaignItemCount()
                    || campaignSource != row.getDeclaredCampaignCount()
                    || querySource < 0L || queryAccounted < 0L
                    || queryAccounted > querySource;
        } catch (ArithmeticException overflow) {
            return true;
        }
    }

    private boolean invalidCount(Long value) { return value == null || value < 0L; }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
