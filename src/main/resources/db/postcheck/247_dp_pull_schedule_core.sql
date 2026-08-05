-- Migration 247 exact postcheck; the migration runner must require result=1.
WITH
expected_table AS (
  SELECT table_name FROM JSON_TABLE(
    '["dp_pull_schedule_rotation","dp_pull_schedule_epoch_sequence","dp_pull_schedule_manifest_seal","dp_pull_schedule_source_epoch","dp_pull_schedule_source_scope"]',
    '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$')) j
),
expected_column AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_rotation","n":"runtime_name","y":"varchar(32)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_rotation","n":"next_operation_ordinal","y":"tinyint unsigned","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_rotation","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_rotation","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_rotation","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_epoch_sequence","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_epoch_sequence","n":"last_epoch_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_epoch_sequence","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_epoch_sequence","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_epoch_sequence","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"cutover_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"expected_scope_count","y":"int","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"expected_manifest_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"seal_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"next_scope_key","y":"varchar(96)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"scanned_scope_count","y":"int","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_manifest_seal","n":"resumable_sha256_state","y":"varchar(512)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"verified_manifest_sha256","y":"char(64)","q":"YES","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_manifest_seal","n":"sealed_at_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_manifest_seal","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"epoch_no","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"cutover_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"active_operation_slot","y":"varchar(16)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"epoch_state","y":"varchar(24)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"reconcile_until_utc","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_one_cursor","y":"varchar(512)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_one_scope_count","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_one_ordered_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_two_cursor","y":"varchar(512)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_two_scope_count","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_source_epoch","n":"pass_two_ordered_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"admission_cursor_scope_key","y":"varchar(96)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"binding_cursor_scope_key","y":"varchar(96)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"missing_binding_cursor_scope_key","y":"varchar(96)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"schedule_cursor_scope_key","y":"varchar(96)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"binding_close_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"not_required"},
{"t":"dp_pull_schedule_source_epoch","n":"version_no","y":"bigint","q":"NO","s":"","o":"","d":"0"},
{"t":"dp_pull_schedule_source_epoch","n":"sealed_at_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"terminal_at_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_epoch","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"operation_code","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"epoch_no","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"source_cursor","y":"varchar(512)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"source_cursor_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"scope_key","y":"varchar(96)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"scope_namespace","y":"varchar(32)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"owner_user_id","y":"bigint","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"logical_store_id","y":"bigint","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"account_key","y":"varchar(160)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"egress_key","y":"varchar(160)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"project_code","y":"varchar(100)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"store_code","y":"varchar(100)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"site_code","y":"varchar(20)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"immutable_payload_sha256","y":"char(64)","q":"NO","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"binding_payload_type","y":"varchar(64)","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"binding_payload_sha256","y":"char(64)","q":"YES","s":"ascii","o":"ascii_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"binding_payload","y":"mediumtext","q":"YES","s":"utf8mb4","o":"utf8mb4_bin","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"binding_effective_from_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"admission_anchor_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"pending"},
{"t":"dp_pull_schedule_source_scope","n":"binding_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"not_required"},
{"t":"dp_pull_schedule_source_scope","n":"reconcile_after_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"schedule_after_utc","y":"datetime(3)","q":"YES","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"schedule_state","y":"varchar(16)","q":"NO","s":"utf8mb4","o":"utf8mb4_bin","d":"pending"},
{"t":"dp_pull_schedule_source_scope","n":"gmt_create","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"},
{"t":"dp_pull_schedule_source_scope","n":"gmt_updated","y":"datetime(3)","q":"NO","s":"","o":"","d":"-"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', column_name VARCHAR(64) PATH '$.n',
    column_type VARCHAR(64) PATH '$.y', is_nullable VARCHAR(3) PATH '$.q',
    character_set_name VARCHAR(64) PATH '$.s', collation_name VARCHAR(64) PATH '$.o',
    default_signature VARCHAR(100) PATH '$.d')) j
),
expected_index AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_rotation","n":"PRIMARY","u":0,"c":"runtime_name"},
{"t":"dp_pull_schedule_epoch_sequence","n":"PRIMARY","u":0,"c":"operation_code"},
{"t":"dp_pull_schedule_manifest_seal","n":"PRIMARY","u":0,"c":"operation_code,cutover_key"},
{"t":"dp_pull_schedule_manifest_seal","n":"idx_dp_schedule_manifest_state","u":1,"c":"seal_state,operation_code,cutover_key"},
{"t":"dp_pull_schedule_source_epoch","n":"PRIMARY","u":0,"c":"operation_code,epoch_no"},
{"t":"dp_pull_schedule_source_epoch","n":"uk_dp_schedule_epoch_active","u":0,"c":"active_operation_slot"},
{"t":"dp_pull_schedule_source_epoch","n":"idx_dp_schedule_epoch_retention","u":1,"c":"operation_code,terminal_at_utc,epoch_no"},
{"t":"dp_pull_schedule_source_scope","n":"PRIMARY","u":0,"c":"operation_code,epoch_no,scope_key"},
{"t":"dp_pull_schedule_source_scope","n":"uk_dp_schedule_scope_cursor","u":0,"c":"operation_code,epoch_no,source_cursor_sha256"},
{"t":"dp_pull_schedule_source_scope","n":"idx_dp_schedule_scope_admission","u":1,"c":"operation_code,epoch_no,admission_anchor_state,scope_key"},
{"t":"dp_pull_schedule_source_scope","n":"idx_dp_schedule_scope_binding","u":1,"c":"operation_code,epoch_no,binding_state,scope_key"},
{"t":"dp_pull_schedule_source_scope","n":"idx_dp_schedule_scope_schedule","u":1,"c":"operation_code,epoch_no,schedule_state,scope_key"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', index_name VARCHAR(64) PATH '$.n',
    non_unique INT PATH '$.u', index_columns VARCHAR(1000) PATH '$.c')) j
),
expected_check AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_rotation","n":"chk_dp_schedule_rotation_singleton","h":"e8e7142cdf2c262a267ece467203b8b064ad68ddc6494c970a268b02604d1e4a"},
{"t":"dp_pull_schedule_rotation","n":"chk_dp_schedule_rotation_ordinal","h":"c3b4d4d630dc14ea9e086f87e0003e61d722c6422e50a2e8179dd4ede6cb249f"},
{"t":"dp_pull_schedule_epoch_sequence","n":"chk_dp_schedule_epoch_sequence_operation","h":"61c182fdf9fea2e5e2e5c990ed79d4fa8c1de4aa4c7cb32512cedc2819420ea9"},
{"t":"dp_pull_schedule_epoch_sequence","n":"chk_dp_schedule_epoch_sequence_value","h":"1133f570888f90efa7b4ae73fc3a7292eb97885b5d050db36dab299a9e68204c"},
{"t":"dp_pull_schedule_manifest_seal","n":"chk_dp_schedule_manifest_operation","h":"61c182fdf9fea2e5e2e5c990ed79d4fa8c1de4aa4c7cb32512cedc2819420ea9"},
{"t":"dp_pull_schedule_manifest_seal","n":"chk_dp_schedule_manifest_count","h":"3b8b710e86dd88a964233eb3ed03f8346da275c2f6d7156249c275ac6dfdb024"},
{"t":"dp_pull_schedule_manifest_seal","n":"chk_dp_schedule_manifest_digest","h":"c7e04d31fa2a82744146f4286709b7bc1b50e3068c9e01cf28e9bcf75a142580"},
{"t":"dp_pull_schedule_manifest_seal","n":"chk_dp_schedule_manifest_state","h":"4c5de4704c6d978f20de196c2a5a21632fbcf7c0c6ac6d13a3e29995d3a393ef"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_operation","h":"61c182fdf9fea2e5e2e5c990ed79d4fa8c1de4aa4c7cb32512cedc2819420ea9"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_number","h":"e8cac16b57828701735a6bf47647ebcbd724867bcab0d8420095a3178d429d8a"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_count","h":"050ca510792c6e64241fc2d384250e4d0e629d481f00a8b31fafb378458e8157"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_digest","h":"90d946696b86b9d283436cc472e8398c6689710549a817ea4a48e60afd6df2c9"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_state","h":"100dbce1192488e4c42b3aff3c318ac6b026edd5b32e51b10d71672fc25fada6"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_active","h":"b67608c296e7e0abe51228f3d5b91d71f995c2c1c41529203dd6be22772c41ae"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_seal","h":"5872f419bd06ebcb94e2bb9f1390df912bb624f3f6a7af05c7deecd9641241d5"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_terminal","h":"cf19e2db7fa738d130f06bdf565b3215bcb3cc5ce30ab9838d9fe2208fa3847d"},
{"t":"dp_pull_schedule_source_epoch","n":"chk_dp_schedule_epoch_binding_close","h":"422aafae32bccb87911cdb69443f4bc1d65bb03afbe76588a3afe82785f5fcf9"},
{"t":"dp_pull_schedule_source_scope","n":"chk_dp_schedule_scope_identity","h":"c4aba35e52796f8c337c8e0e0cd68a10ee76722aec60869b71aa9ee098ca7216"},
{"t":"dp_pull_schedule_source_scope","n":"chk_dp_schedule_scope_digest","h":"d3deb198ba9d9da97b6e9ab53462899faf2b522fbba3a55df7278135c8629c27"},
{"t":"dp_pull_schedule_source_scope","n":"chk_dp_schedule_scope_binding","h":"b91e59d41c141b465189be6e85bf4bcdf20fdc883dd0582a34ecc302e02c65af"},
{"t":"dp_pull_schedule_source_scope","n":"chk_dp_schedule_scope_state","h":"6f0f03ceafe368b1236d1a44a5e21d28f66c91c80bc8ccb683893dd824cf8eff"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', constraint_name VARCHAR(64) PATH '$.n',
    clause_hash CHAR(64) PATH '$.h')) j
),
expected_fk AS (
  SELECT * FROM JSON_TABLE('[
{"t":"dp_pull_schedule_manifest_seal","n":"fk_dp_schedule_manifest_cutover","c":"operation_code,cutover_key","r":"dp_pull_schedule_cutover","rc":"operation_code,cutover_key","d":"NO ACTION","u":"NO ACTION"},
{"t":"dp_pull_schedule_source_epoch","n":"fk_dp_schedule_epoch_manifest","c":"operation_code,cutover_key","r":"dp_pull_schedule_manifest_seal","rc":"operation_code,cutover_key","d":"NO ACTION","u":"NO ACTION"},
{"t":"dp_pull_schedule_source_scope","n":"fk_dp_schedule_scope_epoch","c":"operation_code,epoch_no","r":"dp_pull_schedule_source_epoch","rc":"operation_code,epoch_no","d":"NO ACTION","u":"NO ACTION"}
]', '$[*]' COLUMNS(table_name VARCHAR(64) PATH '$.t', constraint_name VARCHAR(64) PATH '$.n',
    child_columns VARCHAR(1000) PATH '$.c', referenced_table_name VARCHAR(64) PATH '$.r',
    referenced_columns VARCHAR(1000) PATH '$.rc', delete_rule VARCHAR(16) PATH '$.d',
    update_rule VARCHAR(16) PATH '$.u')) j
),
actual_column AS (
  SELECT c.table_name,c.column_name,LOWER(c.column_type) column_type,c.is_nullable,
    COALESCE(LOWER(c.character_set_name),'') character_set_name,
    COALESCE(LOWER(c.collation_name),'') collation_name,
    CASE WHEN c.column_default IS NULL THEN '-' ELSE LOWER(CAST(c.column_default AS CHAR)) END default_signature,
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
  SELECT tc.table_name,tc.constraint_name,tc.enforced,
    SHA2(REPLACE(REPLACE(REPLACE(
      REGEXP_REPLACE(LOWER(cc.check_clause),'[`()[:space:]]+',''),
      CONCAT(CHAR(92),CHAR(39)),CHAR(39)),
      '_utf8mb4',''),'_ascii',''),256) clause_hash
  FROM information_schema.table_constraints tc JOIN expected_table t ON t.table_name=tc.table_name
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
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
)
SELECT IF(
  (SELECT COUNT(*) FROM information_schema.tables x JOIN expected_table e ON e.table_name=x.table_name
    WHERE x.table_schema=DATABASE() AND x.table_type='BASE TABLE' AND UPPER(x.engine)='INNODB'
      AND LOWER(x.table_collation)='utf8mb4_bin')=5
  AND (SELECT COUNT(*) FROM actual_column)=(SELECT COUNT(*) FROM expected_column)
  AND NOT EXISTS (SELECT 1 FROM expected_column e LEFT JOIN actual_column a
    ON a.table_name=e.table_name AND a.column_name=e.column_name
    WHERE a.table_name IS NULL OR a.column_type<>e.column_type OR a.is_nullable<>e.is_nullable
      OR a.character_set_name<>e.character_set_name OR a.collation_name<>e.collation_name
      OR a.default_signature<>e.default_signature OR a.extra<>'' OR a.generation_expression<>'')
  AND (SELECT COUNT(*) FROM actual_index)=(SELECT COUNT(*) FROM expected_index)
  AND NOT EXISTS (SELECT 1 FROM expected_index e LEFT JOIN actual_index a
    ON a.table_name=e.table_name AND a.index_name=e.index_name
    WHERE a.table_name IS NULL OR a.non_unique<>e.non_unique
      OR a.index_columns<>e.index_columns OR a.safe_shape<>1)
  AND (SELECT COUNT(*) FROM actual_check)=(SELECT COUNT(*) FROM expected_check)
  AND NOT EXISTS (SELECT 1 FROM expected_check e LEFT JOIN actual_check a
    ON a.table_name=e.table_name AND a.constraint_name=e.constraint_name
    WHERE a.table_name IS NULL OR a.enforced<>'YES' OR a.clause_hash<>e.clause_hash)
  AND (SELECT COUNT(*) FROM actual_fk)=(SELECT COUNT(*) FROM expected_fk)
  AND NOT EXISTS (SELECT 1 FROM expected_fk e LEFT JOIN actual_fk a
    ON a.table_name=e.table_name AND a.constraint_name=e.constraint_name
    WHERE a.table_name IS NULL OR a.child_columns<>e.child_columns
      OR a.referenced_table_name<>e.referenced_table_name
      OR a.referenced_columns<>e.referenced_columns
      OR a.delete_rule<>e.delete_rule OR a.update_rule<>e.update_rule)
  AND (SELECT COUNT(*) FROM information_schema.triggers tr JOIN expected_table e
    ON e.table_name=tr.event_object_table WHERE tr.trigger_schema=DATABASE())=0
  AND (SELECT COUNT(*) FROM dp_pull_schedule_rotation)=1
  AND (SELECT COUNT(*) FROM dp_pull_schedule_rotation WHERE runtime_name='daily_pull')=1
  AND (SELECT COUNT(*) FROM dp_pull_schedule_epoch_sequence)=11
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_manifest_seal
    WHERE CHAR_LENGTH(resumable_sha256_state)=0 OR gmt_updated<gmt_create)
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_source_epoch
    WHERE gmt_updated<gmt_create)
  AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_source_scope
    WHERE gmt_updated<gmt_create OR CHAR_LENGTH(TRIM(source_cursor))=0
      OR CHAR_LENGTH(TRIM(scope_key))=0 OR CHAR_LENGTH(TRIM(account_key))=0),
  1,0
) AS schedule_core_exact_postcheck;
