-- Migration 250 exact postcheck; the migration runner must require result=1.
WITH actual_check AS (
  SELECT tc.table_name,tc.constraint_name,tc.enforced,
    REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+','') clause_signature
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
    AND ((tc.table_name='dp_pull_advertising_generation' AND tc.constraint_name IN
      ('chk_dp_ad_generation_extent','chk_dp_ad_generation_sealed'))
      OR (tc.table_name='dp_pull_advertising_campaign_fact'
        AND tc.constraint_name='chk_dp_ad_campaign_position'))
)
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='dp_pull_advertising_generation'
      AND column_name='campaign_page_count' AND LOWER(column_type)='int'
      AND is_nullable='NO' AND column_default='1' AND extra=''
      AND generation_expression='')=1
  AND (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='dp_pull_advertising_generation'
      AND column_name='provider_as_of_utc' AND LOWER(column_type)='datetime(3)'
      AND is_nullable='YES' AND column_default IS NULL AND extra=''
      AND generation_expression='')=1
  AND (SELECT COUNT(*) FROM actual_check WHERE enforced='YES')=3
  AND (SELECT COUNT(*) FROM actual_check
    WHERE table_name='dp_pull_advertising_generation'
      AND constraint_name='chk_dp_ad_generation_extent'
      AND LOCATE('campaign_page_count>=1',clause_signature)>0
      AND LOCATE('last_page=campaign_page_count+active_campaign_count',clause_signature)>0)=1
  AND (SELECT COUNT(*) FROM actual_check
    WHERE table_name='dp_pull_advertising_generation'
      AND constraint_name='chk_dp_ad_generation_sealed'
      AND LOCATE('query_page_proof_count=active_campaign_count',clause_signature)>0)=1
  AND (SELECT COUNT(*) FROM actual_check
    WHERE table_name='dp_pull_advertising_campaign_fact'
      AND constraint_name='chk_dp_ad_campaign_position'
      AND LOCATE('page_no>=1',clause_signature)>0
      AND LOCATE('item_ordinal>=0',clause_signature)>0)=1
  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_generation
    WHERE campaign_page_count<1 OR last_page<>campaign_page_count+active_campaign_count
      OR provider_as_of_utc IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_campaign_fact c
    JOIN dp_pull_advertising_generation g ON g.task_id=c.task_id
    WHERE c.page_no<1 OR c.page_no>g.campaign_page_count)
  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_query_fact q
    JOIN dp_pull_advertising_generation g ON g.task_id=q.task_id
    WHERE q.page_no<=g.campaign_page_count OR q.page_no>g.last_page),
  1,0
) AS dp250_exact_postcheck;
