-- Migration 251 additive-compatible livecheck; the migration runner must require result=1.
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery' AND column_name='scope_owner_user_id')=1
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery' AND column_name='scoped_successor_slot')=1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('noon_auth_owner_scope_manifest','noon_auth_owner_scope_manifest_item',
      'noon_auth_owner_scope_audit'))=3
  AND NOT EXISTS (
    SELECT 1 FROM noon_auth_owner_scope_manifest manifest
    LEFT JOIN noon_auth_identity_recovery scoped ON scoped.id=manifest.scoped_recovery_id
    LEFT JOIN noon_auth_identity_recovery source ON source.id=manifest.source_recovery_id
    LEFT JOIN noon_auth_identity_recovery predecessor ON predecessor.id=manifest.predecessor_recovery_id
    WHERE scoped.id IS NULL OR source.id IS NULL OR predecessor.id IS NULL
       OR manifest.owner_user_id<>scoped.scope_owner_user_id
       OR manifest.identity_key<>scoped.identity_key
       OR manifest.identity_key<>source.identity_key
       OR manifest.identity_key<>predecessor.identity_key
       OR scoped.predecessor_recovery_id<>predecessor.id
       OR (manifest.status='ACTIVE' AND (source.status<>'WAITING_PREDECESSOR'
         OR source.version_no<>manifest.source_recovery_version
         OR source.generation_no<>manifest.source_generation_no
         OR source.send_budget_epoch<>manifest.source_send_budget_epoch
         OR source.send_attempt_count<>manifest.source_send_attempt_count))
  )
  AND NOT EXISTS (
    SELECT 1 FROM noon_auth_owner_scope_manifest manifest
    LEFT JOIN (SELECT manifest_id,
        SUM(selected_for_scope=b'1') item_count,
        COUNT(DISTINCT CASE WHEN selected_for_scope=b'1' THEN project_code END) project_count,
        SUM(selected_for_scope=b'0') remaining_item_count,
        COUNT(DISTINCT CASE WHEN selected_for_scope=b'0' THEN project_code END) remaining_project_count
      FROM noon_auth_owner_scope_manifest_item GROUP BY manifest_id) item_count
      ON item_count.manifest_id=manifest.id
    WHERE COALESCE(item_count.item_count,0)<>manifest.item_count
       OR COALESCE(item_count.project_count,0)<>manifest.project_count
       OR COALESCE(item_count.remaining_item_count,0)<>manifest.source_remaining_item_count
       OR COALESCE(item_count.remaining_project_count,0)<>manifest.source_remaining_project_count
  )
  AND NOT EXISTS (
    SELECT 1 FROM noon_auth_owner_scope_manifest_item manifest_item
    JOIN noon_auth_owner_scope_manifest manifest ON manifest.id=manifest_item.manifest_id
    LEFT JOIN noon_auth_identity_recovery_item item
      ON item.id=manifest_item.source_item_id
     AND item.recovery_id=CASE WHEN manifest_item.selected_for_scope=b'1'
       THEN manifest.scoped_recovery_id ELSE manifest.source_recovery_id END
    WHERE manifest.status='ACTIVE'
      AND (item.id IS NULL
       OR (manifest_item.selected_for_scope=b'1' AND item.owner_user_id<>manifest.owner_user_id))
  ),
  1,0
) AS noon_auth_owner_scope_251_additive_livecheck;
