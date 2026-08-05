package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;

/** Versioned, bounded DP-08-B checkpoint (one exact-list result plus evidence). */
final class Dp08ListCheckpoint {
    private static final int VERSION = 1;
    private int schemaVersion;
    private String outcome;
    private NoonProductDetail detail;
    private NoonSearchPage evidence;

    public Dp08ListCheckpoint() {
    }

    static Dp08ListCheckpoint found(NoonProductDetail detail, NoonSearchPage evidence) {
        Dp08ListCheckpoint checkpoint = new Dp08ListCheckpoint();
        checkpoint.schemaVersion = VERSION;
        checkpoint.outcome = "FOUND";
        checkpoint.detail = detail;
        checkpoint.evidence = evidence;
        return checkpoint;
    }

    static Dp08ListCheckpoint notFound(NoonSearchPage evidence) {
        Dp08ListCheckpoint checkpoint = new Dp08ListCheckpoint();
        checkpoint.schemaVersion = VERSION;
        checkpoint.outcome = "NOT_FOUND";
        checkpoint.evidence = evidence;
        return checkpoint;
    }

    String encode(ObjectMapper objectMapper) {
        validate();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception failure) {
            throw new IllegalArgumentException("DP-08-B checkpoint cannot be encoded", failure);
        }
    }

    static Dp08ListCheckpoint decode(ObjectMapper objectMapper, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("DP-08-B checkpoint is missing");
        }
        try {
            Dp08ListCheckpoint checkpoint = objectMapper.readerFor(Dp08ListCheckpoint.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
            checkpoint.validate();
            return checkpoint;
        } catch (Exception failure) {
            throw new IllegalArgumentException("DP-08-B checkpoint is invalid", failure);
        }
    }

    private void validate() {
        if (schemaVersion != VERSION || evidence == null
                || (!"FOUND".equals(outcome) && !"NOT_FOUND".equals(outcome))
                || ("FOUND".equals(outcome) && detail == null)
                || ("NOT_FOUND".equals(outcome) && detail != null)) {
            throw new IllegalArgumentException("unsupported DP-08-B checkpoint state");
        }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public NoonProductDetail getDetail() { return detail; }
    public void setDetail(NoonProductDetail detail) { this.detail = detail; }
    public NoonSearchPage getEvidence() { return evidence; }
    public void setEvidence(NoonSearchPage evidence) { this.evidence = evidence; }
}
