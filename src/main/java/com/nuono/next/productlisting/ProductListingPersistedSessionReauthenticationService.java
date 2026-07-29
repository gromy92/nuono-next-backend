package com.nuono.next.productlisting;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.NoonCatalogConnectionProbe;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingPersistedSessionReauthenticationService {
    private final NoonCatalogConnectionProbe catalogProbe;
    private final ProductListingReauthenticationCommitter committer;

    public ProductListingPersistedSessionReauthenticationService(
            NoonCatalogConnectionProbe catalogProbe,
            ProductListingReauthenticationCommitter committer
    ) {
        this.catalogProbe = catalogProbe;
        this.committer = committer;
    }

    public Optional<ProductListingWorkflowView> reauthenticateIfVerified(
            BusinessAccessContext context,
            ProductListingTaskView task,
            StoreSyncStoreRecord project,
            StoreSyncStoreRecord site,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        String cookie = normalize(project.getNoonPartnerCookie());
        String sessionProjectUser = firstNonBlank(
                project.getNoonPartnerProjectUser(),
                project.getNoonPartnerUserCode(),
                project.getNoonPartnerUser()
        );
        if (!StringUtils.hasText(cookie)
                || !StringUtils.hasText(sessionProjectUser)) {
            return Optional.empty();
        }
        try {
            catalogProbe.verify(
                    task.getOwnerUserId(),
                    sessionProjectUser,
                    cookie,
                    project.getProjectCode(),
                    site.getStoreCode(),
                    resolveSiteCode(site),
                    firstNonBlank(
                            project.getNoonPartnerId(),
                            derivePartnerId(project.getProjectCode())
                    )
            );
        } catch (RuntimeException exception) {
            if (NoonAuthenticationFailureClassifier
                    .isAuthenticationFailure(exception)) {
                return Optional.empty();
            }
            throw new ProductListingReauthenticationException(
                    "Noon 已保存会话的只读验证失败；未发送新的验证码，原上架任务保持不变。",
                    exception
            );
        }
        return Optional.of(committer.commit(
                context,
                new ProductListingReauthenticationCommitter
                        .ReauthenticationCommit(
                        task.getTaskId(),
                        task.getSourceTaskId(),
                        task.getDraftId(),
                        task.getOwnerUserId(),
                        task.getStoreCode(),
                        project.getId(),
                        project.getProjectCode(),
                        firstNonBlank(
                                project.getNoonPartnerUserCode(),
                                project.getNoonPartnerProjectUser(),
                                project.getNoonPartnerUser()
                        ),
                        cookie,
                        resumeAction
                )
        ));
    }

    private String resolveSiteCode(StoreSyncStoreRecord site) {
        if (site != null && StringUtils.hasText(site.getSite())) {
            return site.getSite().trim().toUpperCase(Locale.ROOT);
        }
        String storeCode = site == null ? null : normalize(site.getStoreCode());
        return StringUtils.hasText(storeCode)
                && (storeCode.toUpperCase(Locale.ROOT).endsWith("-NSA")
                || storeCode.toUpperCase(Locale.ROOT).endsWith("-SAU"))
                ? "SA"
                : "AE";
    }

    private String derivePartnerId(String projectCode) {
        String normalized = normalize(projectCode);
        return normalized != null
                && normalized.toUpperCase(Locale.ROOT).startsWith("PRJ")
                ? normalized.substring(3)
                : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
