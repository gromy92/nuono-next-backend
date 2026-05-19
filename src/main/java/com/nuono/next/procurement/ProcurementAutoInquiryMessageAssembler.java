package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementAutoInquiryMessageAssembler {

    public MessageDraft assemble(DemandItemView demandItem, CandidateView candidate) {
        if (demandItem == null || candidate == null) {
            throw new IllegalArgumentException("缺少需求或候选上下文，暂时不能生成自动询价话术。");
        }

        List<String> blocks = new ArrayList<>();
        String openingLine = normalize(candidate.getInquiryOpeningLine());
        String summaryLine = normalize(candidate.getInquirySummaryLine());
        List<String> inquiryQuestions = candidate.getInquiryQuestions() == null
                ? new ArrayList<>()
                : candidate.getInquiryQuestions();
        List<String> quoteChecklist = candidate.getQuoteChecklist() == null
                ? new ArrayList<>()
                : candidate.getQuoteChecklist();

        if (openingLine != null) {
            blocks.add(openingLine);
        }
        if (summaryLine != null) {
            blocks.add(summaryLine);
        }
        if (!inquiryQuestions.isEmpty()) {
            blocks.add(joinNumberedBlock("想先确认以下几点：", inquiryQuestions));
        }
        if (!quoteChecklist.isEmpty()) {
            blocks.add(joinNumberedBlock("报价请一起说明：", quoteChecklist));
        }
        String requirement = normalize(demandItem.getSpecialRequirement());
        if (requirement != null) {
            blocks.add("补充要求：" + requirement);
        }

        String payloadText = String.join("\n\n", blocks).trim();
        if (!StringUtils.hasText(payloadText)) {
            throw new IllegalStateException("当前候选还没有可发送的自动询价内容，请先补齐询价准备信息。");
        }

        MessageDraft draft = new MessageDraft();
        draft.setPayloadText(payloadText);
        draft.setPreviewText(compactPreview(payloadText));
        draft.setPayloadHash(sha256(payloadText));
        return draft;
    }

    private String joinNumberedBlock(String title, List<String> items) {
        List<String> normalizedItems = new ArrayList<>();
        int index = 1;
        for (String item : items) {
            String normalized = normalize(item);
            if (normalized == null) {
                continue;
            }
            normalizedItems.add(index + ". " + normalized);
            index += 1;
        }
        if (normalizedItems.isEmpty()) {
            return "";
        }
        return title + "\n" + String.join("\n", normalizedItems);
    }

    private String compactPreview(String payloadText) {
        String normalized = payloadText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    private String sha256(String payloadText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payloadText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境缺少 SHA-256 算法，暂时不能生成询价幂等摘要。", exception);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static class MessageDraft {

        private String previewText;

        private String payloadText;

        private String payloadHash;

        public String getPreviewText() {
            return previewText;
        }

        public void setPreviewText(String previewText) {
            this.previewText = previewText;
        }

        public String getPayloadText() {
            return payloadText;
        }

        public void setPayloadText(String payloadText) {
            this.payloadText = payloadText;
        }

        public String getPayloadHash() {
            return payloadHash;
        }

        public void setPayloadHash(String payloadHash) {
            this.payloadHash = payloadHash;
        }
    }
}
