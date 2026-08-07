package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthOwnerScopeMapper {
    @Select({
            "SELECT id FROM noon_auth_owner_scope_manifest",
            "WHERE active_identity_slot = #{identityKey}",
            "LIMIT 1 FOR UPDATE"
    })
    Long selectActiveOwnerScopeManifestForUpdate(@Param("identityKey") String identityKey);

    @Update({
            "UPDATE noon_auth_identity_recovery successor",
            "JOIN noon_auth_identity_recovery predecessor ON predecessor.id=successor.predecessor_recovery_id",
            "LEFT JOIN noon_auth_identity_recovery active ON active.identity_key=successor.identity_key",
            " AND active.active_identity_slot IS NOT NULL",
            "LEFT JOIN noon_auth_owner_scope_manifest owner_scope",
            " ON owner_scope.predecessor_recovery_id=predecessor.id AND owner_scope.status='ACTIVE'",
            "SET successor.status='COALESCING', successor.coalesce_until=#{coalesceUntil},",
            " successor.next_attempt_at=#{coalesceUntil}, successor.version_no=successor.version_no+1,",
            " successor.gmt_updated=#{now}",
            "WHERE successor.status='WAITING_PREDECESSOR'",
            " AND predecessor.status IN ('COMPLETED','FAILED_FINAL','CANCELLED')",
            " AND (owner_scope.id IS NULL OR owner_scope.scoped_recovery_id=successor.id)",
            " AND active.id IS NULL"
    })
    int promoteReadySuccessors(@Param("coalesceUntil") LocalDateTime coalesceUntil,
                               @Param("now") LocalDateTime now);

    @Select({
            "SELECT IF(COUNT(*)=1",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_identity_recovery_item item",
            " LEFT JOIN noon_auth_owner_scope_manifest_item frozen",
            " ON frozen.manifest_id=manifest.id AND frozen.source_item_id=item.id",
            " AND frozen.selected_for_scope=b'1'",
            " WHERE item.recovery_id=recovery.id AND (frozen.source_item_id IS NULL",
            " OR frozen.owner_user_id<>item.owner_user_id",
            " OR BINARY frozen.project_code<>BINARY item.project_code",
            " OR NOT(frozen.store_code<=>item.store_code) OR NOT(frozen.site_code<=>item.site_code)",
            " OR NOT(frozen.source_task_id<=>item.source_task_id)",
            " OR NOT(frozen.source_domain<=>item.source_domain)",
            " OR NOT(frozen.source_checkpoint<=>item.source_checkpoint)",
            " OR frozen.resume_policy<>item.resume_policy",
            " OR frozen.expected_auth_version<>item.expected_auth_version))",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_owner_scope_manifest_item frozen",
            " LEFT JOIN noon_auth_identity_recovery_item item",
            " ON item.id=frozen.source_item_id AND item.recovery_id=recovery.id",
            " WHERE frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'1' AND item.id IS NULL)",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_owner_scope_manifest_item frozen",
            " LEFT JOIN noon_project_auth_state state ON frozen.owner_user_id=state.owner_user_id",
            " AND BINARY frozen.project_code=BINARY state.project_code",
            " WHERE frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'1'",
            " AND (state.owner_user_id IS NULL OR state.active_recovery_id<>recovery.id",
            " OR state.auth_version<>frozen.expected_auth_version))",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_identity_recovery_item source_item",
            " WHERE source_item.recovery_id=manifest.source_recovery_id",
            " AND source_item.owner_user_id=manifest.owner_user_id)",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_owner_scope_manifest_item frozen",
            " LEFT JOIN noon_auth_identity_recovery_item item",
            " ON item.id=frozen.source_item_id AND item.recovery_id=manifest.source_recovery_id",
            " WHERE frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'0'",
            " AND (item.id IS NULL OR frozen.owner_user_id<>item.owner_user_id",
            " OR BINARY frozen.project_code<>BINARY item.project_code",
            " OR NOT(frozen.store_code<=>item.store_code) OR NOT(frozen.site_code<=>item.site_code)",
            " OR NOT(frozen.source_task_id<=>item.source_task_id)",
            " OR NOT(frozen.source_domain<=>item.source_domain)",
            " OR NOT(frozen.source_checkpoint<=>item.source_checkpoint)",
            " OR frozen.resume_policy<>item.resume_policy",
            " OR frozen.expected_auth_version<>item.expected_auth_version))",
            " AND NOT EXISTS (SELECT 1 FROM noon_auth_identity_recovery_item item",
            " LEFT JOIN noon_auth_owner_scope_manifest_item frozen",
            " ON frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'0'",
            " AND frozen.source_item_id=item.id",
            " WHERE item.recovery_id=manifest.source_recovery_id AND frozen.source_item_id IS NULL),1,0)",
            "FROM noon_auth_owner_scope_manifest manifest",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id=manifest.scoped_recovery_id",
            "JOIN noon_auth_identity_recovery source ON source.id=manifest.source_recovery_id",
            "JOIN noon_auth_identity_recovery predecessor ON predecessor.id=manifest.predecessor_recovery_id",
            "WHERE recovery.id=#{recoveryId} AND manifest.status='ACTIVE'",
            " AND recovery.scope_owner_user_id=manifest.owner_user_id",
            " AND recovery.identity_key=manifest.identity_key",
            " AND recovery.predecessor_recovery_id=predecessor.id",
            " AND predecessor.status IN ('COMPLETED','FAILED_FINAL','CANCELLED')",
            " AND source.status='WAITING_PREDECESSOR'",
            " AND source.version_no=manifest.source_recovery_version",
            " AND source.send_attempt_count=manifest.source_send_attempt_count",
            " AND source.send_budget_epoch=manifest.source_send_budget_epoch",
            " AND source.generation_no=manifest.source_generation_no"
    })
    int isOwnerScopeManifestValid(@Param("recoveryId") Long recoveryId);
}
