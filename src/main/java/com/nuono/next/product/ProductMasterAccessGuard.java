package com.nuono.next.product;

import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.nuono.next.auth.RoleAccessSupport.isSystemAdmin;

@Service
@Profile("local-db")
public class ProductMasterAccessGuard {

    private final StoreSyncMapper storeSyncMapper;
    private final ProductManagementMapper productManagementMapper;

    public ProductMasterAccessGuard(
            StoreSyncMapper storeSyncMapper,
            ProductManagementMapper productManagementMapper
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.productManagementMapper = productManagementMapper;
    }

    public Long resolveOwnerUserId(
            AuthenticatedSession session,
            Long requestedOwnerUserId,
            String storeCode
    ) {
        requireBusinessSession(session);
        Long sessionUserId = session.getUserId();
        if (StringUtils.hasText(storeCode)) {
            Long accessibleOwnerUserId = storeSyncMapper.selectAccessibleOwnerUserIdForStore(sessionUserId, storeCode);
            if (accessibleOwnerUserId != null) {
                if (requestedOwnerUserId == null || Objects.equals(requestedOwnerUserId, accessibleOwnerUserId)) {
                    return accessibleOwnerUserId;
                }
                throw new ProductMasterAccessDeniedException("无权操作请求中的商品老板上下文。");
            }
        }
        if (requestedOwnerUserId == null || Objects.equals(requestedOwnerUserId, sessionUserId)) {
            return sessionUserId;
        }
        throw new ProductMasterAccessDeniedException("无权操作请求中的商品老板上下文。");
    }

    public void applyScope(AuthenticatedSession session, ProductMasterFetchCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少商品请求参数。");
        }
        command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId(), command.getStoreCode()));
    }

    public void applyScope(AuthenticatedSession session, ProductClassificationOptionsCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少商品分类候选请求参数。");
        }
        command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId(), command.getStoreCode()));
    }

    public Long resolvePublishTaskOwnerUserId(
            AuthenticatedSession session,
            Long taskId,
            Long requestedOwnerUserId
    ) {
        if (taskId == null) {
            throw new IllegalArgumentException("缺少发布任务 ID。");
        }
        ProductPublishTaskRecord task = productManagementMapper.selectProductPublishTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("发布任务不存在或已删除。");
        }
        if (requestedOwnerUserId != null && !Objects.equals(requestedOwnerUserId, task.getOwnerUserId())) {
            throw new ProductMasterAccessDeniedException("无权操作请求中的商品发布任务。");
        }
        return resolveOwnerUserId(session, task.getOwnerUserId(), task.getStoreCode());
    }

    private void requireBusinessSession(AuthenticatedSession session) {
        if (session == null || session.getUserId() == null) {
            throw new ProductMasterAccessDeniedException("请先登录后再操作商品业务。");
        }
        if (isSystemAdmin(session)) {
            throw new ProductMasterAccessDeniedException("系统管理员不能直接操作商品业务，请切换到已授权的业务账号。");
        }
    }
}
