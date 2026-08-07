-- Migration 251 exact postcheck; the migration runner must require result=1.
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery' AND column_name='scope_owner_user_id'
    AND LOWER(column_type)='bigint' AND is_nullable='YES')=1
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery' AND column_name='successor_identity_slot'
    AND LOCATE('waiting_predecessor',LOWER(generation_expression))>0
    AND LOCATE('scope_owner_user_id',LOWER(generation_expression))>0
    AND LOCATE('is null',LOWER(generation_expression))>0)=1
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery' AND column_name='scoped_successor_slot'
    AND LOCATE('waiting_predecessor',LOWER(generation_expression))>0
    AND LOCATE('scope_owner_user_id',LOWER(generation_expression))>0
    AND LOCATE('is not null',LOWER(generation_expression))>0)=1
  AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='noon_auth_identity_recovery'
    AND index_name='uk_noon_auth_recovery_scoped_successor' AND non_unique=0)=1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('noon_auth_owner_scope_manifest','noon_auth_owner_scope_manifest_item',
      'noon_auth_owner_scope_audit'))=3
  AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='noon_auth_owner_scope_manifest'
    AND index_name='uk_noon_auth_owner_scope_manifest_active' AND non_unique=0)=1
  AND NOT EXISTS (SELECT 1 FROM noon_auth_identity_recovery
    WHERE scope_owner_user_id IS NOT NULL AND status<>'WAITING_PREDECESSOR')
  AND NOT EXISTS (SELECT 1 FROM noon_auth_owner_scope_manifest),
  1,0
) AS noon_auth_owner_scope_251_exact_postcheck;
