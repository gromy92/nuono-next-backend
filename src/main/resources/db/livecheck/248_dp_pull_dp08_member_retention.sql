-- Migration 248 additive-compatible livecheck; the runtime must require result=1.
-- Required DP08 member contracts remain exact; safe additive successor metadata is tolerated.
WITH
expected_table AS (
  SELECT table_name FROM JSON_TABLE(
    '["dp_pull_schedule_dp08_member_stage_head","dp_pull_schedule_dp08_member_stage_item","dp_pull_dp08_member_set","dp_pull_dp08_member_set_item","dp_pull_dp08_task_member_progress"]',
    '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$')) j
),
expected_column AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"epoch_no","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"scan_pass","y":"tinyint unsigned","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"scope_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"source_cursor","y":"varchar(512)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"member_count","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"member_ordered_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"base_payload","y":"text","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"effective_from_utc","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"stage_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"member_set_id","y":"char(64)","q":"YES","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"epoch_no","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"scan_pass","y":"tinyint unsigned","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"scope_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"member_key","y":"varchar(64)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"member_kind","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"watch_product_id","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"competitor_product_id","y":"bigint","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"noon_product_code","y":"varchar(64)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"source_updated_at_utc","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"member_set_id","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"scope_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"member_count","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"member_ordered_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"handle_payload_type","y":"varchar(64)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"handle_payload_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"handle_payload","y":"text","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"effective_from_utc","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"set_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"copy_cursor","y":"varchar(64)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"copied_member_count","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_member_set","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_member_set","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"member_set_id","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"member_key","y":"varchar(64)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"member_kind","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"watch_product_id","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"competitor_product_id","y":"bigint","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"noon_product_code","y":"varchar(64)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"source_updated_at_utc","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_member_set_item","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"task_id","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"member_set_id","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"evidence_cursor","y":"varchar(64)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"evidence_member_count","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"evidence_complete","y":"bit(1)","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"exact_search_required","y":"bit(1)","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"apply_cursor","y":"varchar(64)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"applied_member_count","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"apply_complete","y":"bit(1)","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"search_run_id","y":"bigint","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"keyword_run_id","y":"bigint","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"rank_fact_count","y":"int","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_dp08_task_member_progress","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_dp08_task_member_progress","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', column_name VARCHAR(64) PATH '$.n',
    column_type VARCHAR(64) PATH '$.y', is_nullable VARCHAR(3) PATH '$.q',
    character_set_name VARCHAR(64) PATH '$.s', collation_name VARCHAR(64) PATH '$.o',
    default_signature VARCHAR(100) PATH '$.d')) j
),
expected_index AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"PRIMARY","u":0,"c":"operation_code,epoch_no,scan_pass,scope_key"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"idx_dp08_member_stage_set","u":1,"c":"member_set_id"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"PRIMARY","u":0,"c":"operation_code,epoch_no,scan_pass,scope_key,member_key"},
{"t":"dp_pull_dp08_member_set","n":"PRIMARY","u":0,"c":"member_set_id"},
{"t":"dp_pull_dp08_member_set","n":"idx_dp08_member_set_scope","u":1,"c":"operation_code,scope_key,effective_from_utc"},
{"t":"dp_pull_dp08_member_set_item","n":"PRIMARY","u":0,"c":"member_set_id,member_key"},
{"t":"dp_pull_dp08_task_member_progress","n":"PRIMARY","u":0,"c":"task_id"},
{"t":"dp_pull_dp08_task_member_progress","n":"idx_dp08_task_member_set","u":1,"c":"member_set_id,task_id"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', index_name VARCHAR(64) PATH '$.n',
    non_unique INT PATH '$.u', index_columns VARCHAR(1000) PATH '$.c')) j
),
expected_check AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"chk_dp08_member_stage_head_identity"},
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"chk_dp08_member_stage_head_state"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"chk_dp08_member_stage_item_identity"},
{"t":"dp_pull_dp08_member_set","n":"chk_dp08_member_set_identity"},
{"t":"dp_pull_dp08_member_set","n":"chk_dp08_member_set_state"},
{"t":"dp_pull_dp08_member_set_item","n":"chk_dp08_member_set_item_identity"},
{"t":"dp_pull_dp08_task_member_progress","n":"chk_dp08_task_member_progress_identity"},
{"t":"dp_pull_dp08_task_member_progress","n":"chk_dp08_task_member_progress_operation"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', constraint_name VARCHAR(64) PATH '$.n')) j
),
expected_fk AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_dp08_member_stage_head","n":"fk_dp08_member_stage_head_epoch","c":"operation_code,epoch_no","r":"dp_pull_schedule_source_epoch","rc":"operation_code,epoch_no","d":"NO ACTION","u":"NO ACTION"},
{"t":"dp_pull_schedule_dp08_member_stage_item","n":"fk_dp08_member_stage_item_head","c":"operation_code,epoch_no,scan_pass,scope_key","r":"dp_pull_schedule_dp08_member_stage_head","rc":"operation_code,epoch_no,scan_pass,scope_key","d":"NO ACTION","u":"NO ACTION"},
{"t":"dp_pull_dp08_member_set_item","n":"fk_dp08_member_set_item_set","c":"member_set_id","r":"dp_pull_dp08_member_set","rc":"member_set_id","d":"NO ACTION","u":"NO ACTION"},
{"t":"dp_pull_dp08_task_member_progress","n":"fk_dp08_task_member_progress_task","c":"task_id","r":"dp_pull_task","rc":"id","d":"CASCADE","u":"NO ACTION"},
{"t":"dp_pull_dp08_task_member_progress","n":"fk_dp08_task_member_progress_set","c":"member_set_id","r":"dp_pull_dp08_member_set","rc":"member_set_id","d":"NO ACTION","u":"NO ACTION"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', constraint_name VARCHAR(64) PATH '$.n',
    child_columns VARCHAR(1000) PATH '$.c', referenced_table_name VARCHAR(64) PATH '$.r',
    referenced_columns VARCHAR(1000) PATH '$.rc', delete_rule VARCHAR(16) PATH '$.d',
    update_rule VARCHAR(16) PATH '$.u')) j
),
expected_guard AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_source_scope","n":"chk_dp_schedule_scope_dp08_handle_size","c":"binding_payload"},
{"t":"dp_pull_scope_binding_epoch","n":"chk_dp_scope_binding_dp08_handle_size","c":"payload"},
{"t":"dp_pull_task","n":"chk_dp_task_dp08_handle_size","c":"scope_payload"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', constraint_name VARCHAR(64) PATH '$.n',
    payload_column VARCHAR(64) PATH '$.c')) j
),
actual_column AS (
  SELECT c.table_name,c.column_name,LOWER(c.column_type) column_type,c.is_nullable,
    COALESCE(LOWER(c.character_set_name),'') character_set_name,
    COALESCE(LOWER(c.collation_name),'') collation_name,
    CASE WHEN c.column_default IS NULL THEN '-'
      ELSE REPLACE(REPLACE(LOWER(CAST(c.column_default AS CHAR)), 'b''', ''), '''', '')
    END default_signature,
    c.extra,c.generation_expression
  FROM information_schema.columns c JOIN expected_table t ON t.table_name=c.table_name
  WHERE c.table_schema=DATABASE()
),
actual_index AS (
  SELECT s.table_name,s.index_name,MIN(s.non_unique) non_unique,
    GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index SEPARATOR ',') index_columns,
    MIN(s.index_type='BTREE' AND s.is_visible='YES' AND s.sub_part IS NULL
      AND s.expression IS NULL AND s.collation='A') safe_shape
  FROM information_schema.statistics s JOIN expected_table t ON t.table_name=s.table_name
  WHERE s.table_schema=DATABASE() GROUP BY s.table_name,s.index_name
),
actual_check AS (
  SELECT tc.table_name,tc.constraint_name,tc.enforced
  FROM information_schema.table_constraints tc JOIN expected_table t ON t.table_name=tc.table_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
),
actual_fk AS (
  SELECT k.table_name,k.constraint_name,
    GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position SEPARATOR ',') child_columns,
    MAX(k.referenced_table_name) referenced_table_name,
    GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position SEPARATOR ',') referenced_columns,
    MAX(r.delete_rule) delete_rule,MAX(r.update_rule) update_rule
  FROM information_schema.key_column_usage k
  JOIN information_schema.referential_constraints r
    ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name
  JOIN expected_table t ON t.table_name=k.table_name
  WHERE k.constraint_schema=DATABASE() AND k.referenced_table_name IS NOT NULL
  GROUP BY k.table_name,k.constraint_name
),
actual_guard AS (
  SELECT tc.table_name,tc.constraint_name,tc.enforced,
    REGEXP_REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'[()[:space:]]+','') clause_signature
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  JOIN expected_guard e ON e.table_name=tc.table_name AND e.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
)
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.tables x JOIN expected_table e ON e.table_name=x.table_name
    WHERE x.table_schema=DATABASE() AND x.table_type='BASE TABLE' AND UPPER(x.engine)='INNODB'
      AND LOWER(x.table_collation)='utf8mb4_bin')=5
  AND NOT EXISTS (SELECT 1 FROM expected_column e LEFT JOIN actual_column a
    ON a.table_name=e.table_name AND a.column_name=e.column_name
    WHERE a.table_name IS NULL OR a.column_type<>e.column_type OR a.is_nullable<>e.is_nullable
      OR a.character_set_name<>e.character_set_name OR a.collation_name<>e.collation_name
      OR a.default_signature<>e.default_signature OR a.extra<>'' OR a.generation_expression<>'')
  -- Reject a successor-added extra required column; nullable/defaulted/generated additions are safe.
  AND NOT EXISTS (SELECT 1 FROM actual_column a LEFT JOIN expected_column e
    ON e.table_name=a.table_name AND e.column_name=a.column_name
    WHERE e.table_name IS NULL AND NOT (a.is_nullable='YES' OR a.default_signature<>'-'
      OR a.extra LIKE '%auto_increment%' OR a.generation_expression<>''))
  AND NOT EXISTS (SELECT 1 FROM expected_index e LEFT JOIN actual_index a
    ON a.table_name=e.table_name AND a.index_name=e.index_name
    WHERE a.table_name IS NULL OR a.non_unique<>e.non_unique
      OR a.index_columns<>e.index_columns OR a.safe_shape<>1)
  -- Reject a successor-added extra required index; only visible non-unique BTREE indexes are additive.
  AND NOT EXISTS (SELECT 1 FROM actual_index a LEFT JOIN expected_index e
    ON e.table_name=a.table_name AND e.index_name=a.index_name
    WHERE e.table_name IS NULL AND NOT (a.non_unique=1 AND a.safe_shape=1))
  AND NOT EXISTS (SELECT 1 FROM expected_check e LEFT JOIN actual_check a
    ON a.table_name=e.table_name AND a.constraint_name=e.constraint_name
    WHERE a.table_name IS NULL OR a.enforced<>'YES')
  AND NOT EXISTS (SELECT 1 FROM expected_fk e LEFT JOIN actual_fk a
    ON a.table_name=e.table_name AND a.constraint_name=e.constraint_name
    WHERE a.table_name IS NULL OR a.child_columns<>e.child_columns
      OR a.referenced_table_name<>e.referenced_table_name
      OR a.referenced_columns<>e.referenced_columns
      OR a.delete_rule<>e.delete_rule OR a.update_rule<>e.update_rule)
  AND NOT EXISTS (SELECT 1 FROM expected_guard e LEFT JOIN actual_guard a
    ON a.table_name=e.table_name AND a.constraint_name=e.constraint_name
    WHERE a.table_name IS NULL OR a.enforced<>'YES'
      OR LOCATE('dp08a',a.clause_signature)=0 OR LOCATE('dp08b',a.clause_signature)=0
      OR LOCATE(CONCAT('octet_length',e.payload_column,'between1and4096'),a.clause_signature)=0)
  AND (SELECT COUNT(*) FROM information_schema.triggers tr JOIN expected_table e
    ON e.table_name=tr.event_object_table WHERE tr.trigger_schema=DATABASE())=0
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_dp08_member_stage_head h
    WHERE h.gmt_updated<h.gmt_create OR JSON_VALID(h.base_payload)=0
      OR JSON_UNQUOTE(JSON_EXTRACT(h.base_payload,'$.operationCode'))<>h.operation_code
      OR JSON_UNQUOTE(JSON_EXTRACT(h.base_payload,'$.stableScopeKey'))<>h.scope_key)
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_dp08_member_stage_head h
    LEFT JOIN (SELECT operation_code,epoch_no,scan_pass,scope_key,COUNT(*) member_rows
      FROM dp_pull_schedule_dp08_member_stage_item
      GROUP BY operation_code,epoch_no,scan_pass,scope_key) i
    ON i.operation_code=h.operation_code AND i.epoch_no=h.epoch_no
      AND i.scan_pass=h.scan_pass AND i.scope_key=h.scope_key
    WHERE (h.scan_pass=1 AND COALESCE(i.member_rows,0)<>h.member_count)
      OR (h.scan_pass=2 AND COALESCE(i.member_rows,0)<>0))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_dp08_member_set s
    LEFT JOIN (SELECT member_set_id,COUNT(*) member_rows FROM dp_pull_dp08_member_set_item
      GROUP BY member_set_id) i ON i.member_set_id=s.member_set_id
    WHERE s.gmt_updated<s.gmt_create OR COALESCE(i.member_rows,0)<>s.copied_member_count
      OR SHA2(s.handle_payload,256)<>s.handle_payload_sha256 OR JSON_VALID(s.handle_payload)=0
      OR JSON_UNQUOTE(JSON_EXTRACT(s.handle_payload,'$.operationCode'))<>s.operation_code
      OR JSON_UNQUOTE(JSON_EXTRACT(s.handle_payload,'$.stableScopeKey'))<>s.scope_key
      OR JSON_UNQUOTE(JSON_EXTRACT(s.handle_payload,'$.memberSetId'))<>s.member_set_id
      OR CAST(JSON_UNQUOTE(JSON_EXTRACT(s.handle_payload,'$.memberCount')) AS UNSIGNED)<>s.member_count
      OR JSON_UNQUOTE(JSON_EXTRACT(s.handle_payload,'$.memberOrderedSha256'))<>s.member_ordered_sha256
      OR (s.operation_code='DP08A' AND s.handle_payload_type<>'DP08_KEYWORD_MEMBER_SET_V1')
      OR (s.operation_code='DP08B' AND s.handle_payload_type<>'DP08_LIST_MEMBER_SET_V1'))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_dp08_member_stage_head h
    LEFT JOIN dp_pull_dp08_member_set s ON s.member_set_id=h.member_set_id
    WHERE h.member_set_id IS NOT NULL AND (s.member_set_id IS NULL
      OR s.operation_code<>h.operation_code OR s.scope_key<>h.scope_key
      OR s.member_count<>h.member_count OR s.member_ordered_sha256<>h.member_ordered_sha256
      OR s.effective_from_utc<>h.effective_from_utc))
  AND NOT EXISTS (SELECT 1 FROM dp_pull_dp08_task_member_progress p
    JOIN dp_pull_dp08_member_set s ON s.member_set_id=p.member_set_id
    JOIN dp_pull_task t ON t.id=p.task_id
    WHERE p.gmt_updated<p.gmt_create OR p.operation_code<>s.operation_code
      OR p.operation_code<>t.operation_code OR p.evidence_member_count>s.member_count
      OR p.applied_member_count>s.member_count),
  1,0
) AS dp08_member_retention_additive_livecheck;
