package com.nuono.next.datapull.report;

import com.nuono.next.noon.NoonBinaryDownloadContractException;

/** Deterministic local artifact invariant failure; retrying the same input cannot repair it. */
public final class ReportArtifactContractException
        extends NoonBinaryDownloadContractException {

    public ReportArtifactContractException(String code) {
        super(code);
    }
}
