package com.nuono.next.noonads;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NoonAdvertisingImportService {
    public static final String SOURCE_SYSTEM = "noon_ads";
    private static final Pattern NOON_EXTERNAL_SKU_CODE = Pattern.compile("^Z[A-Za-z0-9]+(-[0-9]+)?$");

    private final NoonAdvertisingImportRepository repository;

    public NoonAdvertisingImportService(NoonAdvertisingImportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NoonAdvertisingImportResult importReport(NoonAdvertisingReportImportCommand command) {
        requireValidCommand(command);
        NoonAdvertisingReportBatch batch = new NoonAdvertisingReportBatch();
        batch.setSourceSystem(SOURCE_SYSTEM);
        batch.setSourceName(command.getSourceName());
        batch.setSourceDigestSha256(command.getSourceDigestSha256());
        batch.setOwnerUserId(command.getOwnerUserId());
        batch.setProjectCode(command.getProjectCode());
        batch.setStoreCode(command.getStoreCode());
        batch.setSiteCode(command.getSiteCode());
        batch.setReportDateFrom(command.getDateFrom());
        batch.setReportDateTo(command.getDateTo());
        batch.setStatus("imported");
        batch.setCampaignRowCount(command.getCampaignRows().size());
        batch.setQueryRowCount(command.getQueryRows().size());
        batch.setNotes(command.getNotes());
        batch.setCreatedBy(command.getRequestedBy());
        batch.setUpdatedBy(command.getRequestedBy());

        Long batchId = findExistingBatchId(batch);
        if (batchId == null) {
            batchId = repository.nextReportBatchId();
            batch.setId(batchId);
            repository.insertReportBatch(batch);
        } else {
            batch.setId(batchId);
        }

        for (NoonAdvertisingCampaignFact fact : command.getCampaignRows()) {
            applyScope(fact, command, batchId);
            fact.setId(repository.nextCampaignFactId());
            repository.upsertCampaignFact(fact);
        }
        for (NoonAdvertisingQueryFact fact : command.getQueryRows()) {
            applyScope(fact, command, batchId);
            fact.setId(repository.nextQueryFactId());
            fact.setQueryHash(hashQuery(fact));
            repository.upsertQueryFact(fact);
        }

        return new NoonAdvertisingImportResult(
                batchId,
                command.getCampaignRows().size(),
                command.getQueryRows().size(),
                command.getDateFrom(),
                command.getDateTo()
        );
    }

    private Long findExistingBatchId(NoonAdvertisingReportBatch batch) {
        if (!StringUtils.hasText(batch.getSourceDigestSha256())) {
            return null;
        }
        return repository.findReportBatchId(batch);
    }

    private void requireValidCommand(NoonAdvertisingReportImportCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required.");
        }
        if (command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getProjectCode())
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())
                || command.getDateFrom() == null
                || command.getDateTo() == null) {
            throw new IllegalArgumentException("owner, project, store, site and report window are required.");
        }
        if (command.getDateTo().isBefore(command.getDateFrom())) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom.");
        }
    }

    private void applyScope(NoonAdvertisingCampaignFact fact, NoonAdvertisingReportImportCommand command, Long batchId) {
        fact.setBatchId(batchId);
        fact.setSourceSystem(SOURCE_SYSTEM);
        fact.setOwnerUserId(command.getOwnerUserId());
        fact.setProjectCode(command.getProjectCode());
        fact.setStoreCode(command.getStoreCode());
        fact.setSiteCode(command.getSiteCode());
        fact.setReportDateFrom(command.getDateFrom());
        fact.setReportDateTo(command.getDateTo());
    }

    private void applyScope(NoonAdvertisingQueryFact fact, NoonAdvertisingReportImportCommand command, Long batchId) {
        fact.setBatchId(batchId);
        fact.setSourceSystem(SOURCE_SYSTEM);
        fact.setOwnerUserId(command.getOwnerUserId());
        fact.setProjectCode(command.getProjectCode());
        fact.setStoreCode(command.getStoreCode());
        fact.setSiteCode(command.getSiteCode());
        fact.setReportDateFrom(command.getDateFrom());
        fact.setReportDateTo(command.getDateTo());
        String partnerSku = normalize(fact.getPartnerSku());
        String adSkuCode = normalize(fact.getAdSkuCode());
        if (!StringUtils.hasText(adSkuCode) && looksLikeNoonExternalSkuCode(partnerSku)) {
            adSkuCode = partnerSku;
            partnerSku = "";
        }
        fact.setAdSkuCode(adSkuCode);
        fact.setPartnerSku(partnerSku);
    }

    private String hashQuery(NoonAdvertisingQueryFact fact) {
        String source = String.join("\u001f",
                normalize(fact.getCampaignCode()),
                normalize(fact.getPartnerSku()),
                normalize(fact.getAdSkuCode()),
                normalize(fact.getQueryText()),
                normalize(fact.getQueryKind())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash Noon Ads query row.", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private boolean looksLikeNoonExternalSkuCode(String value) {
        return StringUtils.hasText(value) && NOON_EXTERNAL_SKU_CODE.matcher(value.trim()).matches();
    }
}
