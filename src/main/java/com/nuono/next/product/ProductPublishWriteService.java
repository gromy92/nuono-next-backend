package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductPublishWriteService {

    private final StoreSyncMapper storeSyncMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductGroupPublishService productGroupPublishService;
    private final WriteOperations writeOperations;

    ProductPublishWriteService(
            StoreSyncMapper storeSyncMapper,
            ProductNoonAdapter productNoonAdapter,
            ProductGroupPublishService productGroupPublishService,
            WriteOperations writeOperations
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.productGroupPublishService = productGroupPublishService;
        this.writeOperations = writeOperations;
    }

    void publishSupportedChanges(
            ProductMasterActionCommand command,
            StoreSyncStoreRecord store,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView liveBeforePublish,
            String currentSiteCode,
            ProductPublishUnsupportedChanges unsupportedChanges,
            List<String> actionWarnings
    ) {
        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法执行商品发布。");
        }

        String noonUser = firstNonBlank(
                normalize(command.getNoonUser()),
                normalize(store.getNoonPartnerUser()),
                normalize(store.getNoonPartnerProjectUser()),
                owner.getNoonPartnerUser(),
                owner.getNoonPartnerProjectUser()
        );
        String noonEmailAuthCode = firstNonBlank(
                normalize(store.getNoonPartnerMailAuthCode()),
                normalize(owner.getNoonPartnerMailAuthCode())
        );
        String noonPassword = firstNonBlank(
                normalize(command.getNoonPassword()),
                normalize(store.getNoonPartnerPwd()),
                normalize(owner.getNoonPartnerPwd())
        );
        requireText(noonUser, "当前店铺缺少 Noon 账号上下文，暂时不能发布。");
        requireNoonLoginCredential(noonEmailAuthCode, noonPassword);

        String storeCode = normalize(store.getStoreCode());
        String projectCode = firstNonBlank(store.getProjectCode(), store.getNoonPartnerId(), owner.getNoonPartnerId());
        requireText(projectCode, "当前店铺缺少 Noon projectCode，暂时不能发布。");

        String persistedCookie = firstNonBlank(store.getNoonPartnerCookie(), owner.getNoonPartnerCookie());
        NoonSession session = StringUtils.hasText(noonEmailAuthCode)
                ? productNoonAdapter.loginWithEmailAuthCode(
                owner.getId(),
                noonUser,
                noonEmailAuthCode,
                persistedCookie,
                projectCode,
                storeCode
        )
                : productNoonAdapter.hasConfiguredMerchantEmailLogin()
                ? productNoonAdapter.loginWithConfiguredEmailAuthCode(
                owner.getId(),
                persistedCookie,
                projectCode,
                storeCode
        )
                : productNoonAdapter.login(
                owner.getId(),
                noonUser,
                noonPassword,
                persistedCookie,
                projectCode,
                storeCode
        );
        String resolvedProjectCode = writeOperations.resolveProjectCode(session, projectCode, store, actionWarnings);
        session = writeOperations.withProjectAndStore(session, resolvedProjectCode, storeCode);

        if (writeOperations.sharedZskuChanged(draft, baseline)) {
            writeOperations.publishSharedAttributes(session, draft, baseline, liveBeforePublish, unsupportedChanges, actionWarnings);
        }
        if (writeOperations.groupChanged(draft, baseline)) {
            productGroupPublishService.publishGroupChanges(session, draft, baseline, command.getOwnerUserId(), storeCode);
        }

        List<Map<String, Object>> targetOffers = writeOperations.targetOffers(draft, currentSiteCode);
        Map<String, Map<String, Object>> baselineOffers = writeOperations.baselineOffers(baseline);
        for (Map<String, Object> siteOffer : targetOffers) {
            String siteCode = textValue(siteOffer.get("storeCode"));
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer == null) {
                continue;
            }
            if (!writeOperations.siteOfferChanged(siteOffer, baselineOffer)) {
                continue;
            }
            String resolvedPskuCode = firstNonBlank(
                    textValue(siteOffer.get("pskuCode")),
                    textValue(baselineOffer.get("pskuCode"))
            );
            writeOperations.publishOffer(writeOperations.withStore(session, siteCode), resolvedPskuCode, siteOffer, baselineOffer, actionWarnings);
        }

        appendUnsupportedWarnings(unsupportedChanges, actionWarnings);
    }

    private void appendUnsupportedWarnings(
            ProductPublishUnsupportedChanges unsupportedChanges,
            List<String> actionWarnings
    ) {
        if (unsupportedChanges == null) {
            return;
        }
        if (unsupportedChanges.isGroupChanged()) {
            actionWarnings.add("Group 换组或轴定义暂未写回 Noon，仍保留在诺诺草稿中。");
        }
        if (unsupportedChanges.isVariantStructureChanged()) {
            actionWarnings.add("当前尺码结构存在新增或移除，暂未开启真实 Noon 写回。");
        }
        if (!unsupportedChanges.getUnsupportedAttributeCodes().isEmpty()) {
            actionWarnings.add("有部分复杂属性值暂未写回 Noon，仍保留在诺诺草稿中。");
        }
        if (!unsupportedChanges.getUnsupportedSiteFields().isEmpty()) {
            actionWarnings.add("库存汇总和状态码仍保留展示，本轮发布未写回 Noon。");
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNoonLoginCredential(String noonEmailAuthCode, String noonPassword) {
        if (!StringUtils.hasText(noonEmailAuthCode)
                && !productNoonAdapter.hasConfiguredMerchantEmailLogin()
                && !StringUtils.hasText(noonPassword)) {
            throw new IllegalArgumentException("当前店铺缺少 Noon 邮箱授权码或历史登录密码，暂时不能发布。");
        }
    }

    interface WriteOperations {

        String resolveProjectCode(
                NoonSession session,
                String localProjectCode,
                StoreSyncStoreRecord store,
                List<String> warnings
        );

        NoonSession withProjectAndStore(
                NoonSession session,
                String projectCode,
                String storeCode
        );

        NoonSession withStore(
                NoonSession session,
                String storeCode
        );

        boolean sharedZskuChanged(
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline
        );

        boolean groupChanged(
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline
        );

        void publishSharedAttributes(
                NoonSession session,
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline,
                ProductMasterSnapshotView liveBeforePublish,
                ProductPublishUnsupportedChanges unsupportedChanges,
                List<String> actionWarnings
        );

        List<Map<String, Object>> targetOffers(
                ProductMasterSnapshotView draft,
                String currentSiteCode
        );

        Map<String, Map<String, Object>> baselineOffers(ProductMasterSnapshotView baseline);

        boolean siteOfferChanged(
                Map<String, Object> siteOffer,
                Map<String, Object> baselineOffer
        );

        void publishOffer(
                NoonSession session,
                String pskuCode,
                Map<String, Object> siteOffer,
                Map<String, Object> baselineOffer,
                List<String> actionWarnings
        );
    }
}
