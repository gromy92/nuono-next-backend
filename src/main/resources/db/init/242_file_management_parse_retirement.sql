-- Retire the file-management parse capability without deleting historical parse
-- records, archived uploads, or published logistics/outbound-fee facts.
--
-- REQUIRED CUTOVER PRECONDITION:
--   1. Hold the shared production release lock and stop parse HTTP ingress.
--   2. Stop and drain every old parse-capable JVM, including HTTP requests and schedulers.
--   3. In this same MySQL session, set
--        @nuono_242_all_legacy_parse_runtimes_drained = 1
--      only after the drain has been evidenced.
--   4. Never restart the original parse-capable Jar. Any rollback build must keep
--      every retired parse route tombstoned.

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `nuono_242_file_parse_cutover_ack`;
CREATE TEMPORARY TABLE `nuono_242_file_parse_cutover_ack` (
    `all_legacy_runtimes_drained` TINYINT NOT NULL,
    CONSTRAINT `chk_nuono_242_legacy_runtimes_drained`
        CHECK (`all_legacy_runtimes_drained` = 1)
) ENGINE=InnoDB;

INSERT INTO `nuono_242_file_parse_cutover_ack` (`all_legacy_runtimes_drained`)
VALUES (COALESCE(@nuono_242_all_legacy_parse_runtimes_drained, 0));

DROP TEMPORARY TABLE IF EXISTS `nuono_242_file_parse_retirement_guard`;
CREATE TEMPORARY TABLE `nuono_242_file_parse_retirement_guard` (
    `blocking_task_count` BIGINT NOT NULL,
    CONSTRAINT `chk_nuono_242_no_blocking_tasks`
        CHECK (`blocking_task_count` = 0)
) ENGINE=InnoDB;

INSERT INTO `nuono_242_file_parse_retirement_guard` (`blocking_task_count`)
SELECT COUNT(*)
FROM `file_mgmt_parse_task`
WHERE `is_deleted` = b'0'
  AND (
      LOWER(TRIM(COALESCE(`status`, ''))) NOT IN ('published', 'failed')
      OR NULLIF(TRIM(COALESCE(`locked_by`, '')), '') IS NOT NULL
      OR `locked_at` IS NOT NULL
      OR (`started_at` IS NOT NULL AND `finished_at` IS NULL)
      OR (
          LOWER(TRIM(COALESCE(`status`, ''))) = 'failed'
          AND `next_run_at` IS NOT NULL
      )
  );

UPDATE `user_menu`
SET `status` = 0,
    `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `menu_id` = 9301
  AND (`status` <> 0 OR `is_deleted` = b'0');

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `menu_id` = 9301
  AND `is_deleted` = b'0';

UPDATE `menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE (`id` = 9301 OR `url_path` IN ('/system/file-management', '/system/ai-file-parse'))
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_target_plan_scope`
SET `status` = 'retired',
    `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `status` = 'active'
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_target_plan`
SET `status` = 'retired',
    `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `status` = 'active'
  AND `is_deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `nuono_242_file_parse_retirement_postcheck`;
CREATE TEMPORARY TABLE `nuono_242_file_parse_retirement_postcheck` (
    `active_entry_count` BIGINT NOT NULL,
    CONSTRAINT `chk_nuono_242_no_active_entry`
        CHECK (`active_entry_count` = 0)
) ENGINE=InnoDB;

INSERT INTO `nuono_242_file_parse_retirement_postcheck` (`active_entry_count`)
SELECT
    (SELECT COUNT(*) FROM `menu`
     WHERE (`id` = 9301 OR `url_path` IN ('/system/file-management', '/system/ai-file-parse'))
       AND `is_deleted` = b'0')
  + (SELECT COUNT(*) FROM `role_menu`
     WHERE `menu_id` = 9301 AND `is_deleted` = b'0')
  + (SELECT COUNT(*) FROM `user_menu`
     WHERE `menu_id` = 9301 AND `is_deleted` = b'0')
  + (SELECT COUNT(*) FROM `file_mgmt_parse_target_plan`
     WHERE `status` = 'active' AND `is_deleted` = b'0')
  + (SELECT COUNT(*) FROM `file_mgmt_parse_target_plan_scope`
     WHERE `status` = 'active' AND `is_deleted` = b'0');

DROP TEMPORARY TABLE `nuono_242_file_parse_retirement_postcheck`;
DROP TEMPORARY TABLE `nuono_242_file_parse_retirement_guard`;
DROP TEMPORARY TABLE `nuono_242_file_parse_cutover_ack`;
