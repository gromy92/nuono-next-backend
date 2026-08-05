package com.nuono.next.procurement.aliorder.datapull;

import java.util.regex.Pattern;

/** Typed outcome for one fact segment; a business skip advances no fact cursor. */
public final class Ali1688Dp10FactSegmentResult {
    private static final Pattern CODE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");

    private final Integer nextCursor;
    private final String businessSkipCode;

    private Ali1688Dp10FactSegmentResult(Integer nextCursor, String businessSkipCode) {
        this.nextCursor = nextCursor;
        this.businessSkipCode = businessSkipCode;
    }

    public static Ali1688Dp10FactSegmentResult advanced(int nextCursor) {
        if (nextCursor < 1) {
            throw new IllegalArgumentException("DP10 fact cursor must be positive");
        }
        return new Ali1688Dp10FactSegmentResult(nextCursor, null);
    }

    public static Ali1688Dp10FactSegmentResult businessSkipped(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("DP10 business skip code is invalid");
        }
        return new Ali1688Dp10FactSegmentResult(null, code);
    }

    public boolean isBusinessSkipped() {
        return businessSkipCode != null;
    }

    public int getNextCursor() {
        if (nextCursor == null) {
            throw new IllegalStateException("DP10 business skip has no fact cursor");
        }
        return nextCursor;
    }

    public String getBusinessSkipCode() {
        return businessSkipCode;
    }
}
