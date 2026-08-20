-- Migration 256 exact postcheck; result must be one.
WITH actual_check AS (
  SELECT tc.table_name,tc.constraint_name,tc.enforced,
    REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+','') clause_signature
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema
   AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
    AND ((tc.table_name='dp_pull_snapshot_apply'
      AND tc.constraint_name='chk_dp_snapshot_apply_operation')
      OR (tc.table_name='dp_pull_snapshot_current_head'
      AND tc.constraint_name='chk_dp_snapshot_head_operation'))
)
SELECT IF(
  (SELECT COUNT(*) FROM actual_check WHERE enforced='YES')=2
  AND NOT EXISTS (SELECT 1 FROM actual_check
    WHERE LOCATE('operation_codein',clause_signature)=0
      OR LOCATE('dp02',clause_signature)=0
      OR LOCATE('dp04',clause_signature)=0
      OR LOCATE('dp07a',clause_signature)=0
      OR clause_signature REGEXP 'dp01|dp03|dp05|dp06|dp07b|dp08a|dp08b|dp10')
  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_apply
    WHERE operation_code NOT IN ('DP02','DP04','DP07A'))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_current_head
    WHERE operation_code NOT IN ('DP02','DP04','DP07A')),
  1,0
) AS dp256_exact_postcheck;
