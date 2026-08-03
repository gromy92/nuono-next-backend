SELECT IF(
  NOT EXISTS(
    SELECT 1 FROM `menu`
    WHERE (`id` = 9301
       OR `url_path` IN ('/system/file-management', '/system/ai-file-parse'))
      AND `is_deleted` = b'0'
  )
  AND NOT EXISTS(
    SELECT 1 FROM `role_menu`
    WHERE `menu_id` = 9301 AND `is_deleted` = b'0'
  )
  AND NOT EXISTS(
    SELECT 1 FROM `user_menu`
    WHERE `menu_id` = 9301 AND `is_deleted` = b'0'
  )
  AND NOT EXISTS(
    SELECT 1 FROM `file_mgmt_parse_target_plan`
    WHERE `status` = 'active' AND `is_deleted` = b'0'
  )
  AND NOT EXISTS(
    SELECT 1 FROM `file_mgmt_parse_target_plan_scope`
    WHERE `status` = 'active' AND `is_deleted` = b'0'
  )
  AND NOT EXISTS(
    SELECT 1 FROM `file_mgmt_parse_task`
    WHERE `is_deleted` = b'0'
      AND (
        LOWER(TRIM(COALESCE(`status`, ''))) NOT IN ('published', 'failed')
        OR NULLIF(TRIM(COALESCE(`locked_by`, '')), '') IS NOT NULL
        OR `locked_at` IS NOT NULL
        OR (`started_at` IS NOT NULL AND `finished_at` IS NULL)
        OR (LOWER(TRIM(COALESCE(`status`, ''))) = 'failed'
          AND `next_run_at` IS NOT NULL)
      )
  ),
  1,
  0
);
