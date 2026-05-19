CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

-- Local acceptance fixture correction:
-- chenwu / PRJ244978 currently has only AE in the accepted store-management scope.
UPDATE user_store
SET is_deleted = 1,
    updated_by = 10003,
    gmt_updated = NOW()
WHERE user_id = 307
  AND project_code = 'PRJ244978'
  AND store_code IN ('STR244978-NEG', 'STR244978-NSA')
  AND is_deleted = 0;

UPDATE store_data_user_mapping
SET is_deleted = 1,
    updated_by = 10003,
    gmt_updated = NOW()
WHERE owner_user_id = 307
  AND store_code IN ('STR244978-NEG', 'STR244978-NSA')
  AND is_deleted = 0;
