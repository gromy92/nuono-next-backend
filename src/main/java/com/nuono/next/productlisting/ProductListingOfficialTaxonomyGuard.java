package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingOfficialTaxonomyMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class ProductListingOfficialTaxonomyGuard {

    private static final Pattern OFFICIAL_CODE =
            Pattern.compile("^[a-z0-9_]+-[a-z0-9_]+-[a-z0-9_]+$");
    private final ProductListingOfficialTaxonomyMapper mapper;
    private final ProductListingRealWriteProperties properties;

    ProductListingOfficialTaxonomyGuard(
            ProductListingOfficialTaxonomyMapper mapper,
            ProductListingRealWriteProperties properties
    ) {
        this.mapper = mapper;
        this.properties = properties;
    }

    List<ProductListingValidationIssue> validateAndHydrate(
            ProductListingDraftCommand command
    ) {
        String code = normalize(command == null
                ? null
                : command.getProductFullType());
        if (mapper == null || properties == null || !properties.isEnabled()
                || !StringUtils.hasText(code)
                || !OFFICIAL_CODE.matcher(code).matches()) {
            return List.of();
        }
        ProductListingOfficialTaxonomyRecord taxonomy;
        try {
            taxonomy = mapper.selectOfficialNoonProductFulltype(code);
        } catch (RuntimeException exception) {
            return List.of(issue(
                    "noon_product_fulltype_catalog_unavailable",
                    "Noon 官方类目目录暂时不可用，dry-run 已安全阻止真实上架。"
            ));
        }
        if (taxonomy == null) {
            return List.of(issue(
                    "noon_product_fulltype_not_found",
                    "所选 Product Fulltype 不存在于 Noon 官方类目，请重新选择："
                            + code
            ));
        }
        command.setIdProductFullType(taxonomy.getIdProductFulltype());
        command.setFamily(taxonomy.getFamilyNameEn());
        command.setProductType(taxonomy.getProductTypeNameEn());
        command.setProductSubType(taxonomy.getProductSubtypeNameEn());
        return List.of();
    }

    private ProductListingValidationIssue issue(String code, String message) {
        return new ProductListingValidationIssue(
                "productFullType",
                "error",
                code,
                message
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
