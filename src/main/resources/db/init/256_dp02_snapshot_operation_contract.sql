-- Migration 256: admit DP02 into the shared two-pass snapshot seal tables.
SET NAMES utf8mb4;

SET @dp256_apply_old_shape := (SELECT COUNT(*)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema
   AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE()
    AND tc.table_name='dp_pull_snapshot_apply'
    AND tc.constraint_name='chk_dp_snapshot_apply_operation'
    AND tc.constraint_type='CHECK' AND tc.enforced='YES'
    AND LOCATE('operation_codein',REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0
    AND LOCATE('dp04',LOWER(cc.check_clause))>0
    AND LOCATE('dp07a',LOWER(cc.check_clause))>0
    AND LOCATE('dp02',LOWER(cc.check_clause))=0
    AND LOWER(cc.check_clause) NOT REGEXP 'dp01|dp03|dp05|dp06|dp07b|dp08a|dp08b|dp10');
SET @dp256_apply_new_shape := (SELECT COUNT(*)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema
   AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE()
    AND tc.table_name='dp_pull_snapshot_apply'
    AND tc.constraint_name='chk_dp_snapshot_apply_operation'
    AND tc.constraint_type='CHECK' AND tc.enforced='YES'
    AND LOCATE('operation_codein',REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0
    AND LOCATE('dp02',LOWER(cc.check_clause))>0
    AND LOCATE('dp04',LOWER(cc.check_clause))>0
    AND LOCATE('dp07a',LOWER(cc.check_clause))>0
    AND LOWER(cc.check_clause) NOT REGEXP 'dp01|dp03|dp05|dp06|dp07b|dp08a|dp08b|dp10');
SET @dp256_apply_sql := IF(
  @dp256_apply_old_shape=1 AND @dp256_apply_new_shape=0,
  'ALTER TABLE `dp_pull_snapshot_apply`
     DROP CHECK `chk_dp_snapshot_apply_operation`,
     ADD CONSTRAINT `chk_dp_snapshot_apply_operation`
       CHECK (`operation_code` IN (''DP02'',''DP04'',''DP07A''))',
  IF(@dp256_apply_old_shape=0 AND @dp256_apply_new_shape=1,
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''DP256_APPLY_OPERATION_PREDECESSOR_DRIFT'''
  )
);
PREPARE dp256_apply_stmt FROM @dp256_apply_sql;
EXECUTE dp256_apply_stmt;
DEALLOCATE PREPARE dp256_apply_stmt;

SET @dp256_head_old_shape := (SELECT COUNT(*)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema
   AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE()
    AND tc.table_name='dp_pull_snapshot_current_head'
    AND tc.constraint_name='chk_dp_snapshot_head_operation'
    AND tc.constraint_type='CHECK' AND tc.enforced='YES'
    AND LOCATE('operation_codein',REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0
    AND LOCATE('dp04',LOWER(cc.check_clause))>0
    AND LOCATE('dp07a',LOWER(cc.check_clause))>0
    AND LOCATE('dp02',LOWER(cc.check_clause))=0
    AND LOWER(cc.check_clause) NOT REGEXP 'dp01|dp03|dp05|dp06|dp07b|dp08a|dp08b|dp10');
SET @dp256_head_new_shape := (SELECT COUNT(*)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema
   AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE()
    AND tc.table_name='dp_pull_snapshot_current_head'
    AND tc.constraint_name='chk_dp_snapshot_head_operation'
    AND tc.constraint_type='CHECK' AND tc.enforced='YES'
    AND LOCATE('operation_codein',REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+',''))>0
    AND LOCATE('dp02',LOWER(cc.check_clause))>0
    AND LOCATE('dp04',LOWER(cc.check_clause))>0
    AND LOCATE('dp07a',LOWER(cc.check_clause))>0
    AND LOWER(cc.check_clause) NOT REGEXP 'dp01|dp03|dp05|dp06|dp07b|dp08a|dp08b|dp10');
SET @dp256_head_sql := IF(
  @dp256_head_old_shape=1 AND @dp256_head_new_shape=0,
  'ALTER TABLE `dp_pull_snapshot_current_head`
     DROP CHECK `chk_dp_snapshot_head_operation`,
     ADD CONSTRAINT `chk_dp_snapshot_head_operation`
       CHECK (`operation_code` IN (''DP02'',''DP04'',''DP07A''))',
  IF(@dp256_head_old_shape=0 AND @dp256_head_new_shape=1,
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''DP256_HEAD_OPERATION_PREDECESSOR_DRIFT'''
  )
);
PREPARE dp256_head_stmt FROM @dp256_head_sql;
EXECUTE dp256_head_stmt;
DEALLOCATE PREPARE dp256_head_stmt;
