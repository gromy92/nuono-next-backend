package com.nuono.next.productlisting;

import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.MerchantAuthorization;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.NoonCatalogConnectionProbe;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingPasswordReauthenticationService {
    private final NoonSessionGateway sessionGateway;
    private final NoonCatalogConnectionProbe catalogProbe;
    private final ProductListingReauthenticationCommitter committer;

    public ProductListingPasswordReauthenticationService(
            NoonSessionGateway sessionGateway,
            NoonCatalogConnectionProbe catalogProbe,
            ProductListingReauthenticationCommitter committer
    ) {
        this.sessionGateway = sessionGateway;
        this.catalogProbe = catalogProbe;
        this.committer = committer;
    }

    public ProductListingWorkflowView reauthenticate(
            BusinessAccessContext context,
            ProductListingTaskView task,
            StoreSyncStoreRecord project,
            StoreSyncStoreRecord site,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        String loginUser = firstNonBlank(
                project.getNoonPartnerUser(),
                project.getNoonPartnerProjectUser(),
                project.getNoonPartnerUserCode()
        );
        if (!StringUtils.hasText(loginUser)
                || !StringUtils.hasText(project.getNoonPartnerPwd())) {
            throw conflict(
                    "当前 Noon Project 没有可用于重新授权的存储凭证，请先联系店铺负责人。"
            );
        }
        try {
            MerchantAuthorization authorization =
                    sessionGateway.authorizeMerchantLoginCandidate(
                            task.getOwnerUserId(),
                            loginUser,
                            project.getNoonPartnerPwd(),
                            project.getProjectCode(),
                            site.getStoreCode()
                    );
            requireExactProject(authorization, project.getProjectCode());
            String cookie = normalize(authorization.getCookie());
            String sessionProjectUser = firstNonBlank(
                    project.getNoonPartnerProjectUser(),
                    authorization.getUserCode(),
                    project.getNoonPartnerUserCode(),
                    loginUser
            );
            if (!StringUtils.hasText(cookie)
                    || !StringUtils.hasText(sessionProjectUser)) {
                throw new IllegalStateException(
                        "Noon authorization returned no project session."
                );
            }
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
            return committer.commit(
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
                                    authorization.getUserCode(),
                                    project.getNoonPartnerUserCode()
                            ),
                            cookie,
                            resumeAction
                    )
            );
        } catch (ProductListingReauthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProductListingReauthenticationException(
                    "Noon 重新授权未通过，只读商品接口验证未完成；原上架任务保持不变。",
                    exception
            );
        }
    }

    private void requireExactProject(
            MerchantAuthorization authorization,
            String expectedProjectCode
    ) {
        if (authorization == null
                || !authorization.isSuccess()
                || authorization.getSelectedProject() == null
                || !expectedProjectCode.equalsIgnoreCase(
                        authorization.getSelectedProject().getProjectCode()
                )) {
            throw new IllegalStateException(
                    "Noon account did not authorize the target project."
            );
        }
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

    private ProductListingReauthenticationException conflict(String message) {
        return new ProductListingReauthenticationException(message);
    }
}
