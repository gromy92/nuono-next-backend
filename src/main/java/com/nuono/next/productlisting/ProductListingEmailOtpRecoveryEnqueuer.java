package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingEmailOtpRecoveryEnqueuer {
    private final ProductListingReauthenticationAttemptMapper attemptMapper;
    private final NoonProjectAuthRecoveryQueue recoveryQueue;
    private final NoonAuthRecoveryProperties properties;
    private final NoonSessionGateway sessionGateway;

    @Autowired
    public ProductListingEmailOtpRecoveryEnqueuer(
            ProductListingReauthenticationAttemptMapper attemptMapper,
            ObjectProvider<NoonProjectAuthRecoveryQueue> queueProvider,
            NoonAuthRecoveryProperties properties,
            NoonSessionGateway sessionGateway
    ) {
        this(
                attemptMapper,
                queueProvider == null ? null : queueProvider.getIfAvailable(),
                properties,
                sessionGateway
        );
    }

    ProductListingEmailOtpRecoveryEnqueuer(
            ProductListingReauthenticationAttemptMapper attemptMapper,
            NoonProjectAuthRecoveryQueue recoveryQueue,
            NoonAuthRecoveryProperties properties,
            NoonSessionGateway sessionGateway
    ) {
        this.attemptMapper = attemptMapper;
        this.recoveryQueue = recoveryQueue;
        this.properties = properties;
        this.sessionGateway = sessionGateway;
    }

    @Transactional
    public void enqueue(
            ProductListingTaskView task,
            StoreSyncStoreRecord project,
            StoreSyncStoreRecord site,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        ProductListingReauthenticationAttemptRecord locked =
                attemptMapper.selectAttemptForUpdate(
                        task.getTaskId(),
                        task.getOwnerUserId()
                );
        if (locked != null && oneOf(locked.getStatus(), "PENDING", "VERIFYING")) {
            return;
        }
        if (locked != null && "COMPLETED".equals(locked.getStatus())) {
            throw conflict("该上架任务的授权恢复已经完成，请刷新流程。");
        }
        requireQueueConfiguration(project.getProjectCode());
        try {
            Long recoveryId = recoveryQueue.enqueueProject(
                    task.getOwnerUserId(),
                    project.getProjectCode(),
                    site.getStoreCode()
            ).orElseThrow(() -> conflict(
                    "Noon 邮箱授权恢复未能排队；请确认 migration 190、"
                            + "Project 绑定和恢复白名单配置。"
            ));
            ProductListingReauthenticationAttemptRecord recoveryItem =
                    attemptMapper.selectSourceLessRecoveryItem(
                            recoveryId,
                            task.getOwnerUserId(),
                            project.getProjectCode()
                    );
            requireExactRecoveryItem(recoveryItem, recoveryId);
            ProductListingReauthenticationAttemptRecord replacement =
                    pendingAttempt(
                            task,
                            project,
                            recoveryItem,
                            resumeAction
                    );
            if (locked == null) {
                attemptMapper.insertPendingAttempt(replacement);
                ProductListingReauthenticationAttemptRecord persisted =
                        attemptMapper.selectAttemptForUpdate(
                                task.getTaskId(),
                                task.getOwnerUserId()
                        );
                if (!sameBinding(persisted, replacement)
                        || !"PENDING".equals(persisted.getStatus())) {
                    throw conflict(
                            "上架授权恢复关联未能保存；请确认 migration 205 已应用。"
                    );
                }
                return;
            }
            if (!"FAILED".equals(locked.getStatus())
                    || attemptMapper.rebindFailedAttemptCas(
                            replacement,
                            locked.getRecoveryId(),
                            locked.getRecoveryItemId(),
                            locked.getVersionNo()
                    ) != 1) {
                throw conflict("上架授权恢复状态已变化，请刷新后重试。");
            }
        } catch (ProductListingReauthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProductListingReauthenticationException(
                    "Noon 邮箱授权恢复队列不可用；请确认 migration 190、201 "
                            + "及授权恢复配置，原上架任务保持不变。",
                    exception
            );
        }
    }

    private void requireQueueConfiguration(String projectCode) {
        if (properties == null || !properties.isEnabled()) {
            throw conflict(
                    "请先设置 NUONO_NOON_AUTH_RECOVERY_ENABLED=true。"
            );
        }
        if (properties.normalizedTrustedSenderDomains().isEmpty()) {
            throw conflict(
                    "请配置 NUONO_NOON_AUTH_RECOVERY_TRUSTED_SENDER_DOMAINS "
                            + "后再恢复 Noon 授权。"
            );
        }
        if (!properties.allowsProject(projectCode)) {
            throw conflict(
                    "当前 Project 不在 NUONO_NOON_AUTH_RECOVERY_PROJECT_ALLOWLIST 中。"
            );
        }
        try {
            sessionGateway.configuredMerchantEmail();
        } catch (RuntimeException exception) {
            throw new ProductListingReauthenticationException(
                    "请配置 NUONO_NOON_MERCHANT_EMAIL_OTP_EMAIL 和 "
                            + "NUONO_NOON_MERCHANT_EMAIL_OTP_MAIL_AUTH_CODE。",
                    exception
            );
        }
        if (recoveryQueue == null) {
            throw conflict(
                    "共享授权恢复队列未启用；请确认 local-db 配置和 migration 190。"
            );
        }
    }

    private ProductListingReauthenticationAttemptRecord pendingAttempt(
            ProductListingTaskView task,
            StoreSyncStoreRecord project,
            ProductListingReauthenticationAttemptRecord recoveryItem,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        ProductListingReauthenticationAttemptRecord attempt =
                new ProductListingReauthenticationAttemptRecord();
        attempt.setRealRunTaskId(task.getTaskId());
        attempt.setOwnerUserId(task.getOwnerUserId());
        attempt.setDraftId(task.getDraftId());
        attempt.setProjectId(project.getId());
        attempt.setProjectCode(project.getProjectCode());
        attempt.setStoreCode(task.getStoreCode());
        attempt.setRecoveryId(recoveryItem.getRecoveryId());
        attempt.setRecoveryItemId(recoveryItem.getRecoveryItemId());
        attempt.setRequestedAuthVersion(
                recoveryItem.getRequestedAuthVersion()
        );
        attempt.setResumeAction(resumeAction.name());
        attempt.setStatus("PENDING");
        attempt.setVersionNo(0L);
        return attempt;
    }

    private void requireExactRecoveryItem(
            ProductListingReauthenticationAttemptRecord item,
            Long recoveryId
    ) {
        if (item == null
                || item.getRecoveryItemId() == null
                || item.getRequestedAuthVersion() == null
                || !recoveryId.equals(item.getRecoveryId())) {
            throw conflict(
                    "Noon 授权批次缺少 source-less Project 关联；"
                            + "请确认 migration 190 已完整应用。"
            );
        }
    }

    private boolean sameBinding(
            ProductListingReauthenticationAttemptRecord left,
            ProductListingReauthenticationAttemptRecord right
    ) {
        return left != null
                && same(left.getRecoveryId(), right.getRecoveryId())
                && same(left.getRecoveryItemId(), right.getRecoveryItemId())
                && same(
                        left.getRequestedAuthVersion(),
                        right.getRequestedAuthVersion()
                )
                && sameText(left.getProjectCode(), right.getProjectCode())
                && sameText(left.getStoreCode(), right.getStoreCode())
                && sameText(left.getResumeAction(), right.getResumeAction());
    }

    private boolean oneOf(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean same(Object left, Object right) {
        return left != null && left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return left != null
                && right != null
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private ProductListingReauthenticationException conflict(String message) {
        return new ProductListingReauthenticationException(message);
    }
}
