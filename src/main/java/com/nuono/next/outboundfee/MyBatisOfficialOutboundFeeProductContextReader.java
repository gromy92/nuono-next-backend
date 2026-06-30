package com.nuono.next.outboundfee;

import com.nuono.next.infrastructure.mapper.OfficialOutboundFeeProductMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@Profile("local-db")
public class MyBatisOfficialOutboundFeeProductContextReader implements OfficialOutboundFeeProductContextReader {

    private final OfficialOutboundFeeProductMapper mapper;

    public MyBatisOfficialOutboundFeeProductContextReader(OfficialOutboundFeeProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OfficialOutboundFeeProductContext> findContext(
            Long ownerUserId,
            String storeCode,
            String site,
            String skuId
    ) {
        Long variantId = resolveVariantId(ownerUserId, storeCode, skuId);
        if (variantId == null) {
            return Optional.empty();
        }
        OfficialOutboundFeeProductSpecRecord effectiveSpec = mapper.selectProductVariantEffectiveSpecForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                variantId
        );
        BigDecimal salePrice = mapper.selectProductSiteSalePriceForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                variantId,
                site
        );
        return Optional.of(new OfficialOutboundFeeProductContext(
                skuId.trim(),
                variantId,
                effectiveSpec,
                salePrice
        ));
    }

    @Override
    public Optional<OfficialOutboundFeeProductContext> findContextBySpecSource(
            Long ownerUserId,
            String storeCode,
            String site,
            String skuId,
            String sourceType
    ) {
        Long variantId = resolveVariantId(ownerUserId, storeCode, skuId);
        if (variantId == null) {
            return Optional.empty();
        }
        OfficialOutboundFeeProductSpecRecord identity = mapper.selectProductVariantIdentityForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                variantId
        );
        OfficialOutboundFeeProductSpecSourceView source = mapper.selectProductVariantSpecSourceForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                variantId,
                sourceType
        );
        BigDecimal salePrice = mapper.selectProductSiteSalePriceForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                variantId,
                site
        );
        return Optional.of(new OfficialOutboundFeeProductContext(
                skuId.trim(),
                variantId,
                source == null ? null : toSpecRecord(identity, source),
                salePrice
        ));
    }

    private Long resolveVariantId(Long ownerUserId, String storeCode, String skuId) {
        if (!StringUtils.hasText(skuId)) {
            return null;
        }
        String normalizedSkuId = skuId.trim();
        Long variantIdCandidate = parseLong(normalizedSkuId);
        return mapper.selectProductVariantIdForOfficialOutboundFee(
                ownerUserId,
                storeCode,
                normalizedSkuId,
                variantIdCandidate
        );
    }

    private OfficialOutboundFeeProductSpecRecord toSpecRecord(
            OfficialOutboundFeeProductSpecRecord identity,
            OfficialOutboundFeeProductSpecSourceView source
    ) {
        OfficialOutboundFeeProductSpecRecord record = identity == null ? new OfficialOutboundFeeProductSpecRecord() : identity;
        record.setEffectiveSourceId(source.getSourceId());
        record.setEffectiveSourceType(source.getSourceType());
        record.setProductLengthCm(source.getProductLengthCm());
        record.setProductWidthCm(source.getProductWidthCm());
        record.setProductHeightCm(source.getProductHeightCm());
        record.setProductWeightG(source.getProductWeightG());
        record.setSourceType(source.getSourceType());
        record.setConfirmedAt(source.getConfirmedAt());
        record.setConfirmedBy(source.getConfirmedBy());
        return record;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
