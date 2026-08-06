-- Migration 250: evolve DP06 from one fictional dashboard page to verified P campaign pages.
SET NAMES utf8mb4;
SET @dp250_campaign_page_column_count := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='dp_pull_advertising_generation'
    AND column_name='campaign_page_count');
SET @dp250_old_shape_count := (SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema=DATABASE() AND constraint_type='CHECK'
    AND ((table_name='dp_pull_advertising_generation' AND constraint_name IN
      ('chk_dp_ad_generation_extent','chk_dp_ad_generation_sealed'))
      OR (table_name='dp_pull_advertising_campaign_fact'
        AND constraint_name='chk_dp_ad_campaign_position')));
SET @dp250_generation_current_shape := (
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='dp_pull_advertising_generation'
      AND column_name='campaign_page_count' AND LOWER(column_type)='int'
      AND is_nullable='NO' AND column_default='1' AND extra=''
      AND generation_expression='')
  + (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='dp_pull_advertising_generation'
      AND column_name='provider_as_of_utc' AND LOWER(column_type)='datetime(3)'
      AND is_nullable='YES' AND column_default IS NULL AND extra=''
      AND generation_expression='')
  + (SELECT COUNT(*) FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
    WHERE tc.constraint_schema=DATABASE()
      AND tc.table_name='dp_pull_advertising_generation'
      AND tc.constraint_name='chk_dp_ad_generation_extent' AND tc.enforced='YES'
      AND LOCATE('campaign_page_count>=1',
        REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0
      AND LOCATE('last_page=campaign_page_count+active_campaign_count',
        REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0)
  + (SELECT COUNT(*) FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
    WHERE tc.constraint_schema=DATABASE()
      AND tc.table_name='dp_pull_advertising_generation'
      AND tc.constraint_name='chk_dp_ad_generation_sealed' AND tc.enforced='YES'
      AND LOCATE('query_page_proof_count=active_campaign_count',
        REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0)
);
SET @dp250_apply_sql := IF(
  @dp250_campaign_page_column_count=0 AND @dp250_old_shape_count=3,
  'ALTER TABLE `dp_pull_advertising_generation`
     ADD COLUMN `campaign_page_count` INT NOT NULL DEFAULT 1 AFTER `active_campaign_count`,
     MODIFY COLUMN `provider_as_of_utc` DATETIME(3) DEFAULT NULL,
     DROP CHECK `chk_dp_ad_generation_extent`,
     DROP CHECK `chk_dp_ad_generation_sealed`,
     ADD CONSTRAINT `chk_dp_ad_generation_extent` CHECK (`declared_campaign_count`>=0 AND `campaign_page_count`>=1 AND `active_campaign_count` BETWEEN 0 AND `declared_campaign_count` AND `last_page`=`campaign_page_count`+`active_campaign_count` AND `staged_campaign_item_count`>=0 AND `campaign_business_skipped_item_count`>=0 AND `staged_campaign_item_count`+`campaign_business_skipped_item_count`=`declared_campaign_count` AND `staged_item_count`>=`staged_campaign_item_count`+`active_campaign_count` AND `business_skipped_item_count`>=`campaign_business_skipped_item_count` AND `source_item_count`=`staged_item_count`+`business_skipped_item_count`),
     ADD CONSTRAINT `chk_dp_ad_generation_sealed` CHECK (`state`<>''SEALED'' OR (`processed_item_count`=`staged_item_count` AND `campaign_fact_count`+`campaign_identity_skipped_item_count`=`staged_campaign_item_count` AND `staged_campaign_item_count`+`campaign_business_skipped_item_count`=`declared_campaign_count` AND `query_page_proof_count`=`active_campaign_count` AND `source_item_count`-`active_campaign_count`-`declared_campaign_count`=`query_fact_count`+`identity_skipped_item_count`-`campaign_identity_skipped_item_count`+`business_skipped_item_count`-`campaign_business_skipped_item_count` AND `campaign_fact_count`+`query_fact_count`+`identity_skipped_item_count`+`query_page_proof_count`=`staged_item_count`))',
  IF(@dp250_campaign_page_column_count=1 AND @dp250_generation_current_shape=4,
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''DP250_GENERATION_PREDECESSOR_DRIFT'''
  )
);
PREPARE dp250_apply_stmt FROM @dp250_apply_sql;
EXECUTE dp250_apply_stmt;
DEALLOCATE PREPARE dp250_apply_stmt;

SET @dp250_campaign_position_current := (SELECT COUNT(*)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE()
    AND tc.table_name='dp_pull_advertising_campaign_fact'
    AND tc.constraint_name='chk_dp_ad_campaign_position' AND tc.enforced='YES'
    AND REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+','')
      LIKE '%page_no>=1%item_ordinal>=0%');
SET @dp250_campaign_position_sql := IF(
  @dp250_campaign_position_current=0,
  'ALTER TABLE `dp_pull_advertising_campaign_fact`
     DROP CHECK `chk_dp_ad_campaign_position`,
     ADD CONSTRAINT `chk_dp_ad_campaign_position` CHECK (`page_no`>=1 AND `item_ordinal`>=0)',
  'DO 0'
);
PREPARE dp250_campaign_position_stmt FROM @dp250_campaign_position_sql;
EXECUTE dp250_campaign_position_stmt;
DEALLOCATE PREPARE dp250_campaign_position_stmt;
