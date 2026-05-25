package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

class FileParseCommissionSourceScopePolicy {

    <T> List<T> referralFeeSectionRows(List<T> rows, Function<T, String> textExtractor) {
        List<T> scopedRows = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return scopedRows;
        }
        boolean inReferralFees = false;
        for (T row : rows) {
            String text = textExtractor == null ? "" : nullToEmpty(textExtractor.apply(row));
            if (!inReferralFees && isReferralFeesSectionStart(text)) {
                inReferralFees = true;
            } else if (inReferralFees && isReferralFeesSectionEnd(text)) {
                break;
            }
            if (inReferralFees) {
                scopedRows.add(row);
            }
        }
        return scopedRows;
    }

    private boolean isReferralFeesSectionStart(String value) {
        String text = normalizeSectionText(value);
        return text.matches("^1\\.\\s*referral fees\\b.*")
                || text.matches("^referral fees\\s+as\\s+a\\s+%\\s+of\\s+the\\b.*");
    }

    private boolean isReferralFeesSectionEnd(String value) {
        String text = normalizeSectionText(value);
        return text.matches("^2\\.\\s*fbn outbound fees\\b.*")
                || text.matches("^fbn outbound fees\\b.*")
                || text.matches("^3\\.\\s*monthly storage fees\\b.*")
                || text.matches("^monthly storage fees\\b.*")
                || text.matches("^ii\\.\\s*circumstantial fees\\b.*")
                || text.matches("^iii\\.\\s*inventory removal fee\\b.*")
                || text.matches("^inventory removal fee\\b.*")
                || text.matches("^iv\\.\\s*value added services\\b.*")
                || text.matches("^value added services\\b.*")
                || text.matches("^(v\\.|[0-9]+\\.)\\s*faq\\b.*")
                || text.matches("^faq\\b.*")
                || text.matches("^frequently asked questions\\b.*");
    }

    private String normalizeSectionText(String value) {
        return nullToEmpty(value)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
