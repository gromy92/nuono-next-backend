-- Migration 244 exact postcheck; the migration runner must require result=1.
WITH
expected_table AS (
  SELECT table_name FROM JSON_TABLE('["dp_pull_report_artifact","dp_pull_report_artifact_chunk","dp_pull_report_stage","dp_pull_report_stage_row"]','$[*]' COLUMNS(table_name VARCHAR(64) PATH '$')) j
),
expected_column AS (
  SELECT * FROM JSON_TABLE('[["dp_pull_report_artifact","artifact_key","varchar(96)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_artifact","task_id","bigint","NO","","","#NULL",""],["dp_pull_report_artifact","stable_request_key","varchar(96)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_artifact","remote_handle","varchar(512)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_artifact","content_sha256","char(64)","YES","ascii","ascii_bin","#NULL",""],["dp_pull_report_artifact","content_length","bigint","NO","","","0",""],["dp_pull_report_artifact","content_bytes","longblob","YES","","","#NULL",""],["dp_pull_report_artifact","download_state","varchar(20)","NO","utf8mb4","utf8mb4_bin","legacy_complete",""],["dp_pull_report_artifact","persisted_chunk_count","int","NO","","","0",""],["dp_pull_report_artifact","download_fence_epoch","bigint","NO","","","0",""],["dp_pull_report_artifact","downloaded_byte_count","bigint","NO","","","0",""],["dp_pull_report_artifact","downloaded_chunk_count","int","NO","","","0",""],["dp_pull_report_artifact","resumable_sha256_state","varchar(220)","NO","ascii","ascii_bin","v1:0:6a09e667bb67ae853c6ef372a54ff53a510e527f9b05688c1f83d9ab5be0cd19:",""],["dp_pull_report_artifact","expected_content_length","bigint","YES","","","#NULL",""],["dp_pull_report_artifact","source_validator","varchar(512)","YES","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_artifact","created_at","datetime(3)","NO","","","#NULL",""],["dp_pull_report_artifact","updated_at","datetime(3)","NO","","","current_timestamp(3)","default_generated"],["dp_pull_report_artifact_chunk","artifact_key","varchar(96)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_artifact_chunk","chunk_no","int","NO","","","#NULL",""],["dp_pull_report_artifact_chunk","byte_offset","bigint","NO","","","#NULL",""],["dp_pull_report_artifact_chunk","content_length","int","NO","","","#NULL",""],["dp_pull_report_artifact_chunk","content_sha256","char(64)","NO","ascii","ascii_bin","#NULL",""],["dp_pull_report_artifact_chunk","content_bytes","mediumblob","NO","","","#NULL",""],["dp_pull_report_artifact_chunk","created_at","datetime(3)","NO","","","#NULL",""],["dp_pull_report_stage","task_id","bigint","NO","","","#NULL",""],["dp_pull_report_stage","operation_code","varchar(16)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage","artifact_key","varchar(96)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage","artifact_sha256","char(64)","NO","ascii","ascii_bin","#NULL",""],["dp_pull_report_stage","active_fence_epoch","bigint","NO","","","#NULL",""],["dp_pull_report_stage","state","varchar(32)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage","header_json","mediumtext","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage","next_byte_offset","bigint","NO","","","#NULL",""],["dp_pull_report_stage","declared_row_count","bigint","NO","","","#NULL",""],["dp_pull_report_stage","source_row_count","bigint","NO","","","0",""],["dp_pull_report_stage","accepted_row_count","bigint","NO","","","0",""],["dp_pull_report_stage","business_skipped_row_count","bigint","NO","","","0",""],["dp_pull_report_stage","identity_skipped_row_count","bigint","NO","","","0",""],["dp_pull_report_stage","apply_row_cursor","bigint","NO","","","0",""],["dp_pull_report_stage","applied_row_count","bigint","NO","","","0",""],["dp_pull_report_stage","applied_warning_count","bigint","NO","","","0",""],["dp_pull_report_stage","fact_container_id","bigint","YES","","","#NULL",""],["dp_pull_report_stage","poison_code","varchar(80)","YES","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage","version_no","bigint","NO","","","0",""],["dp_pull_report_stage","gmt_create","datetime(3)","NO","","","#NULL",""],["dp_pull_report_stage","gmt_updated","datetime(3)","NO","","","#NULL",""],["dp_pull_report_stage_row","task_id","bigint","NO","","","#NULL",""],["dp_pull_report_stage_row","row_number","bigint","NO","","","#NULL",""],["dp_pull_report_stage_row","decision","varchar(32)","NO","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage_row","identity_sha256","char(64)","YES","ascii","ascii_bin","#NULL",""],["dp_pull_report_stage_row","accepted_identity_sha256","char(64)","YES","ascii","ascii_bin","#NULL",""],["dp_pull_report_stage_row","payload_json","mediumtext","YES","utf8mb4","utf8mb4_bin","#NULL",""],["dp_pull_report_stage_row","gmt_create","datetime(3)","NO","","","#NULL",""]]','$[*]' COLUMNS(table_name VARCHAR(64) PATH '$[0]',column_name VARCHAR(64) PATH '$[1]',column_type VARCHAR(64) PATH '$[2]',is_nullable VARCHAR(3) PATH '$[3]',character_set_name VARCHAR(64) PATH '$[4]',collation_name VARCHAR(64) PATH '$[5]',default_signature VARCHAR(220) PATH '$[6]',extra VARCHAR(100) PATH '$[7]')) j
),
expected_index AS (
  SELECT * FROM JSON_TABLE('[["dp_pull_report_artifact","PRIMARY",0,"artifact_key"],["dp_pull_report_artifact","idx_dp_report_artifact_retention",1,"task_id,created_at,artifact_key"],["dp_pull_report_artifact","idx_dp_report_artifact_download_state",1,"download_state,updated_at,artifact_key"],["dp_pull_report_artifact_chunk","PRIMARY",0,"artifact_key,chunk_no"],["dp_pull_report_artifact_chunk","uk_dp_report_artifact_chunk_offset",0,"artifact_key,byte_offset"],["dp_pull_report_stage","PRIMARY",0,"task_id"],["dp_pull_report_stage","idx_dp_report_stage_artifact",1,"artifact_key,task_id"],["dp_pull_report_stage","idx_dp_report_stage_retention",1,"state,gmt_updated,task_id"],["dp_pull_report_stage_row","PRIMARY",0,"task_id,row_number"],["dp_pull_report_stage_row","uk_dp_report_stage_accepted_identity",0,"task_id,accepted_identity_sha256"],["dp_pull_report_stage_row","idx_dp_report_stage_row_apply",1,"task_id,decision,row_number"]]','$[*]' COLUMNS(table_name VARCHAR(64) PATH '$[0]',index_name VARCHAR(64) PATH '$[1]',non_unique INT PATH '$[2]',index_columns VARCHAR(1000) PATH '$[3]')) j
),
expected_check AS (
  SELECT * FROM JSON_TABLE('[["dp_pull_report_artifact","chk_dp_report_artifact_identity","5844aca1e20f1f3f9761b0475f8f787d8b455561df922370de4427a4e71e81a1"],["dp_pull_report_artifact","chk_dp_report_artifact_digest","3be5b651b6446a353eb8f94954683fbb65af1d813e35343be4eb6554989b7a3e"],["dp_pull_report_artifact","chk_dp_report_artifact_length","5a390b0be1c6539dd6840b551b0921a425cd81905fad648229cb1ca03734a983"],["dp_pull_report_artifact","chk_dp_report_artifact_download_state","3cb690da0339dc6ff463c0049daf5603cfc8e1391eca81ed4a9e00d3973660af"],["dp_pull_report_artifact","chk_dp_report_artifact_chunk_count","9ef89ea281cbc5f7b9fa1597f8d2ef96a2371bdd651c5e111f2a997764cbe432"],["dp_pull_report_artifact","chk_dp_report_artifact_download_progress","5cce15fc0b386e01859201ecb773e41a71f63a0e3747751fca11945a1dfb3795"],["dp_pull_report_artifact","chk_dp_report_artifact_storage_shape","6d837275bbe29fdbc992a003245c2f0acce1385d10519ccc21a805c47344f734"],["dp_pull_report_artifact_chunk","chk_dp_report_artifact_chunk_number","b058ec2e004de531ba62f3dae6ae95272c639a00ed5a7fbe22e0ed16f9db49d6"],["dp_pull_report_artifact_chunk","chk_dp_report_artifact_chunk_offset","2b6124b16faa693e78473a0eaac46ccf4566d13e5d4abb2c11076bb939d5469d"],["dp_pull_report_artifact_chunk","chk_dp_report_artifact_chunk_length","37a468fcc7a0a659a90744321118f366d1c112b9d2ac4e85c9210ee8d88daca4"],["dp_pull_report_artifact_chunk","chk_dp_report_artifact_chunk_digest","1b2a8cb68ddb2c488841f3b6ee303ec839a5d2e754b07594df226067d4bb9ac9"],["dp_pull_report_stage","chk_dp_report_stage_operation","530a20a9dfad0c34fd218e5fc38e1497e4ab40e73f9d71176bbd241c76d72783"],["dp_pull_report_stage","chk_dp_report_stage_identity","ede1554dc298a8969104cb1676bd086d93b0f57f2cbdedae6166866c191da35f"],["dp_pull_report_stage","chk_dp_report_stage_header","3bc0bf1f282598e19e9d93e4568cfc45d9381a346f2b0c08662d3235d2a4347f"],["dp_pull_report_stage","chk_dp_report_stage_counters","f3c9c57bd6795c2fba41d026e21ddc6384d8b23d248f202ee7bc3bd73da19221"],["dp_pull_report_stage","chk_dp_report_stage_state","f1b4aa7fdbaad1c26b23a344eb7863439d652da6d647497af13b7b7be15b89f9"],["dp_pull_report_stage","chk_dp_report_stage_lifecycle","28fd7e0332f064420a6b078f6689a00ba157e5694192370754d581080283645b"],["dp_pull_report_stage","chk_dp_report_stage_poison","32e8e138f29dcdafd2f53643996fd40a7ac45cf5a1804f66b59a156d439fa17f"],["dp_pull_report_stage","chk_dp_report_stage_container","736b256b774ab691e5a14c6f7573fa7177ebc1a03011a000c940bd9ab95c93b0"],["dp_pull_report_stage_row","chk_dp_report_stage_row_number","8990b3d576d1a017ad1025123a6791fb8da0237f7eba7edbdc3ff5abefa7956a"],["dp_pull_report_stage_row","chk_dp_report_stage_row_decision","f51d5ffc1e5de3e487660d8771b054e80f33a84b06a8407c95b716177cde6e03"],["dp_pull_report_stage_row","chk_dp_report_stage_row_shape","18df6cf21cbba841590cf1c8786a052f782d57ab936ffaf3f2de9b12ee724b6b"]]','$[*]' COLUMNS(table_name VARCHAR(64) PATH '$[0]',constraint_name VARCHAR(64) PATH '$[1]',clause_sha256 CHAR(64) PATH '$[2]')) j
),
expected_fk AS (
  SELECT * FROM JSON_TABLE('[["dp_pull_report_artifact","fk_dp_report_artifact_task","task_id","dp_pull_task","id","NO ACTION","NO ACTION"],["dp_pull_report_artifact_chunk","fk_dp_report_artifact_chunk_manifest","artifact_key","dp_pull_report_artifact","artifact_key","NO ACTION","NO ACTION"],["dp_pull_report_stage","fk_dp_report_stage_task","task_id","dp_pull_task","id","NO ACTION","NO ACTION"],["dp_pull_report_stage","fk_dp_report_stage_artifact","artifact_key","dp_pull_report_artifact","artifact_key","NO ACTION","NO ACTION"],["dp_pull_report_stage_row","fk_dp_report_stage_row_stage","task_id","dp_pull_report_stage","task_id","NO ACTION","NO ACTION"]]','$[*]' COLUMNS(table_name VARCHAR(64) PATH '$[0]',constraint_name VARCHAR(64) PATH '$[1]',child_columns VARCHAR(1000) PATH '$[2]',referenced_table_name VARCHAR(64) PATH '$[3]',referenced_columns VARCHAR(1000) PATH '$[4]',delete_rule VARCHAR(16) PATH '$[5]',update_rule VARCHAR(16) PATH '$[6]')) j
),
actual_column AS (
  SELECT c.table_name,c.column_name,LOWER(c.column_type) column_type,c.is_nullable,
    COALESCE(LOWER(c.character_set_name),'') character_set_name,COALESCE(LOWER(c.collation_name),'') collation_name,
    CASE WHEN c.column_default IS NULL THEN '#NULL' ELSE LOWER(c.column_default) END default_signature,
    LOWER(c.extra) extra,c.generation_expression
  FROM information_schema.columns c JOIN expected_table e ON e.table_name=c.table_name
  WHERE c.table_schema=DATABASE()
),
actual_index AS (
  SELECT s.table_name,s.index_name,MIN(s.non_unique) non_unique,
    GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index SEPARATOR ',') index_columns,
    MIN(s.index_type='BTREE' AND s.is_visible='YES' AND s.sub_part IS NULL AND s.expression IS NULL AND s.collation='A') safe_shape
  FROM information_schema.statistics s JOIN expected_table e ON e.table_name=s.table_name
  WHERE s.table_schema=DATABASE() GROUP BY s.table_name,s.index_name
),
actual_check AS (
  SELECT tc.table_name,tc.constraint_name,tc.enforced,
    SHA2(REPLACE(REGEXP_REPLACE(LOWER(cc.check_clause),'[`[:space:]()]',''),'_utf8mb4',''),256) clause_sha256
  FROM information_schema.table_constraints tc JOIN expected_table e ON e.table_name=tc.table_name
  JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
),
actual_fk AS (
  SELECT k.table_name,k.constraint_name,GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position SEPARATOR ',') child_columns,
    MAX(k.referenced_table_name) referenced_table_name,GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position SEPARATOR ',') referenced_columns,
    MAX(r.delete_rule) delete_rule,MAX(r.update_rule) update_rule
  FROM information_schema.key_column_usage k JOIN expected_table e ON e.table_name=k.table_name
  JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name
  WHERE k.constraint_schema=DATABASE() AND k.referenced_table_name IS NOT NULL GROUP BY k.table_name,k.constraint_name
),
chunk_shape AS (
  SELECT a.artifact_key,a.download_state,a.downloaded_byte_count,a.downloaded_chunk_count,a.persisted_chunk_count,a.content_length,
    COUNT(c.chunk_no) actual_chunks,COALESCE(SUM(c.content_length),0) actual_bytes,MIN(c.chunk_no) first_chunk,MAX(c.chunk_no) last_chunk,
    COALESCE(SUM(c.chunk_no<a.downloaded_chunk_count-1 AND c.content_length<>1048576),0) short_nonfinal
  FROM dp_pull_report_artifact a LEFT JOIN dp_pull_report_artifact_chunk c ON c.artifact_key=a.artifact_key
  GROUP BY a.artifact_key,a.download_state,a.downloaded_byte_count,a.downloaded_chunk_count,a.persisted_chunk_count,a.content_length
),
row_shape AS (
  SELECT s.task_id,s.source_row_count,s.accepted_row_count,s.business_skipped_row_count,s.identity_skipped_row_count,
    COUNT(r.row_number) actual_rows,MIN(r.row_number) first_row,MAX(r.row_number) last_row,
    COALESCE(SUM(r.decision='ACCEPTED'),0) accepted_rows,COALESCE(SUM(r.decision='BUSINESS_SKIP'),0) business_rows,
    COALESCE(SUM(r.decision='LATER_IDENTITY_CONFLICT'),0) identity_rows
  FROM dp_pull_report_stage s LEFT JOIN dp_pull_report_stage_row r ON r.task_id=s.task_id
  GROUP BY s.task_id,s.source_row_count,s.accepted_row_count,s.business_skipped_row_count,s.identity_skipped_row_count
)
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.tables t JOIN expected_table e ON e.table_name=t.table_name
    WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE' AND UPPER(t.engine)='INNODB' AND LOWER(t.table_collation)='utf8mb4_bin')=4
  AND (SELECT COUNT(*) FROM actual_column)=52 AND (SELECT COUNT(*) FROM actual_index)=11
  AND (SELECT COUNT(*) FROM actual_check)=22 AND (SELECT COUNT(*) FROM actual_fk)=5
  AND NOT EXISTS (SELECT 1 FROM expected_column e LEFT JOIN actual_column a USING(table_name,column_name)
    WHERE a.table_name IS NULL OR a.column_type<>e.column_type OR a.is_nullable<>e.is_nullable OR a.character_set_name<>e.character_set_name
      OR a.collation_name<>e.collation_name OR a.default_signature<>e.default_signature OR a.extra<>e.extra OR a.generation_expression<>'')
  AND NOT EXISTS (SELECT 1 FROM expected_index e LEFT JOIN actual_index a USING(table_name,index_name)
    WHERE a.table_name IS NULL OR a.non_unique<>e.non_unique OR a.index_columns<>e.index_columns OR a.safe_shape<>1)
  AND NOT EXISTS (SELECT 1 FROM expected_check e LEFT JOIN actual_check a USING(table_name,constraint_name)
    WHERE a.table_name IS NULL OR a.enforced<>'YES' OR a.clause_sha256<>e.clause_sha256)
  AND NOT EXISTS (SELECT 1 FROM expected_fk e LEFT JOIN actual_fk a USING(table_name,constraint_name)
    WHERE a.table_name IS NULL OR a.child_columns<>e.child_columns OR a.referenced_table_name<>e.referenced_table_name
      OR a.referenced_columns<>e.referenced_columns OR a.delete_rule<>e.delete_rule OR a.update_rule<>e.update_rule)
  AND (SELECT COUNT(*) FROM information_schema.triggers tr JOIN expected_table e ON e.table_name=tr.event_object_table WHERE tr.trigger_schema=DATABASE())=0
  AND NOT EXISTS (SELECT 1 FROM chunk_shape WHERE actual_chunks<>downloaded_chunk_count OR actual_bytes<>downloaded_byte_count
    OR (actual_chunks=0 AND (first_chunk IS NOT NULL OR last_chunk IS NOT NULL))
    OR (actual_chunks>0 AND (first_chunk<>0 OR last_chunk<>actual_chunks-1 OR short_nonfinal<>0))
    OR (download_state='LEGACY_COMPLETE' AND actual_chunks<>0)
    OR (download_state='COMPLETE' AND (actual_chunks<>persisted_chunk_count OR actual_bytes<>content_length)))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage s JOIN dp_pull_task t ON t.id=s.task_id
    JOIN dp_pull_report_artifact a ON a.artifact_key=s.artifact_key
    WHERE t.operation_code<>s.operation_code OR a.task_id<>s.task_id OR s.active_fence_epoch>t.fence_epoch
      OR a.download_state<>'COMPLETE' OR a.content_sha256<>s.artifact_sha256
      OR (s.state IN ('SEALED','EMPTY_UNPROVEN','APPLIED') AND a.content_length<>s.next_byte_offset))
  AND NOT EXISTS (SELECT 1 FROM row_shape WHERE actual_rows<>source_row_count OR accepted_rows<>accepted_row_count
    OR business_rows<>business_skipped_row_count OR identity_rows<>identity_skipped_row_count
    OR (actual_rows=0 AND (first_row IS NOT NULL OR last_row IS NOT NULL))
    OR (actual_rows>0 AND (first_row<>1 OR last_row<>actual_rows)))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage s LEFT JOIN dp_pull_report_apply a ON a.task_id=s.task_id
    LEFT JOIN dp_pull_task t ON t.id=s.task_id WHERE s.state='APPLIED'
      AND (a.task_id IS NULL OR a.operation_code<>s.operation_code OR a.scope_key<>t.scope_key
        OR a.business_window_key<>t.business_window_key OR a.applied_fence_epoch<>s.active_fence_epoch
        OR s.apply_row_cursor<>s.source_row_count)),
  1,0
) AS dp244_exact_postcheck;
