package com.nuono.next.productselection;

import com.nuono.next.product.ProductContentTranslateCommand;
import com.nuono.next.product.ProductContentTranslateView;
import com.nuono.next.product.ProductContentTranslationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductSelectionSourceCollectionLocalizer {

    private final ObjectProvider<ProductContentTranslationService> translationServiceProvider;

    public ProductSelectionSourceCollectionLocalizer(
            ObjectProvider<ProductContentTranslationService> translationServiceProvider
    ) {
        this.translationServiceProvider = translationServiceProvider;
    }

    public ProductSelectionSourceCollectionResult localize(
            ProductSelectionSourceCollectionRow existing,
            ProductSelectionSourceCollectionResult collected
    ) {
        ProductSelectionSourceCollectionResult result = collected == null
                ? new ProductSelectionSourceCollectionResult()
                : collected;

        String existingChineseTitle = firstText(
                existing == null ? null : existing.getSourceTitleCn(),
                cjkText(existing == null ? null : existing.getSelectedText())
        );
        if (StringUtils.hasText(existingChineseTitle) && !StringUtils.hasText(result.getSourceTitleCn())) {
            result.setSourceTitleCn(existingChineseTitle);
        }

        if (!StringUtils.hasText(result.getSourceTitleCn()) && StringUtils.hasText(result.getSourceTitle())) {
            result.setSourceTitleCn(translateText(result.getSourceTitle(), "AUTO", "ZH", existingOperatorUserId(existing)));
        }
        if (!StringUtils.hasText(result.getSourceTitleAr()) && StringUtils.hasText(result.getSourceTitle())) {
            result.setSourceTitleAr(translateText(result.getSourceTitle(), "AUTO", "AR", existingOperatorUserId(existing)));
        }
        if (!StringUtils.hasText(result.getSourceDescriptionEn()) && StringUtils.hasText(result.getSelectedText())) {
            result.setSourceDescriptionEn(result.getSelectedText());
        }
        if (!StringUtils.hasText(result.getSourceDescriptionAr()) && StringUtils.hasText(result.getSourceDescriptionEn())) {
            result.setSourceDescriptionAr(translateText(result.getSourceDescriptionEn(), "AUTO", "AR", existingOperatorUserId(existing)));
        }
        if ((result.getSourceSellingPointsAr() == null || result.getSourceSellingPointsAr().isEmpty())
                && result.getSourceSellingPointsEn() != null
                && !result.getSourceSellingPointsEn().isEmpty()) {
            result.setSourceSellingPointsAr(result.getSourceSellingPointsEn().stream()
                    .map(point -> translateText(point, "AUTO", "AR", existingOperatorUserId(existing)))
                    .map(this::compactText)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList()));
        }
        if (!StringUtils.hasText(result.getSelectedTextAr()) && StringUtils.hasText(result.getSourceDescriptionAr())) {
            result.setSelectedTextAr(result.getSourceDescriptionAr());
        }
        if (!StringUtils.hasText(result.getSelectedTextAr()) && StringUtils.hasText(result.getSelectedText())) {
            result.setSelectedTextAr(translateText(result.getSelectedText(), "AUTO", "AR", existingOperatorUserId(existing)));
        }
        return result;
    }

    private String translateText(String text, String sourceLang, String targetLang, Long operatorUserId) {
        ProductContentTranslationService translationService = translationServiceProvider.getIfAvailable();
        if (translationService == null || !StringUtils.hasText(text)) {
            return "";
        }
        try {
            ProductContentTranslateCommand command = new ProductContentTranslateCommand();
            command.setText(text);
            command.setSourceLang(sourceLang);
            command.setTargetLang(targetLang);
            command.setOperatorUserId(operatorUserId);
            ProductContentTranslateView view = translationService.translate(command);
            if (!view.isReady()) {
                return "";
            }
            return extractTranslationText(view);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTranslationText(ProductContentTranslateView view) {
        Object translation = view.getData().get("translation");
        if (!(translation instanceof Map<?, ?>)) {
            return "";
        }
        Object text = ((Map<String, Object>) translation).get("text");
        return text == null ? "" : compactText(String.valueOf(text));
    }

    private Long existingOperatorUserId(ProductSelectionSourceCollectionRow existing) {
        return existing == null ? null : existing.getUpdatedBy();
    }

    private String cjkText(String value) {
        String text = compactText(value);
        return containsCjk(text) ? text : "";
    }

    private boolean containsCjk(String value) {
        for (int index = 0; index < value.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(index));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return compactText(value);
            }
        }
        return "";
    }

    private String compactText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

}
