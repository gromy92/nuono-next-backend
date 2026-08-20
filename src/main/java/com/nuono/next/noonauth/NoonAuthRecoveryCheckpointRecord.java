package com.nuono.next.noonauth;

import java.time.LocalDateTime;

public final class NoonAuthRecoveryCheckpointRecord {
    private Long recoveryId;
    private Integer generationNo;
    private String checkpointKind;
    private String keyVersion;
    private byte[] initializationVector;
    private byte[] ciphertext;
    private LocalDateTime expiresAt;
    private Long versionNo;

    public Long getRecoveryId() { return recoveryId; }
    public void setRecoveryId(Long value) { recoveryId = value; }
    public Integer getGenerationNo() { return generationNo; }
    public void setGenerationNo(Integer value) { generationNo = value; }
    public String getCheckpointKind() { return checkpointKind; }
    public void setCheckpointKind(String value) { checkpointKind = value; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String value) { keyVersion = value; }
    public byte[] getInitializationVector() { return clone(initializationVector); }
    public void setInitializationVector(byte[] value) { initializationVector = clone(value); }
    public byte[] getCiphertext() { return clone(ciphertext); }
    public void setCiphertext(byte[] value) { ciphertext = clone(value); }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long value) { versionNo = value; }

    private static byte[] clone(byte[] value) {
        return value == null ? null : value.clone();
    }
}
