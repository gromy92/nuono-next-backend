-- Materialize the dated Yite quote and add product-level transport eligibility.
-- The legacy numeric-adjustment tables remain byte-semantically unchanged for old-Jar reads and rollback.
SET SESSION `lock_wait_timeout` = 5; SET SESSION `innodb_lock_wait_timeout` = 5; SET SESSION `group_concat_max_len` = 1048576;
SET @required_table_count := (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name IN ('forwarder_quote_version','forwarder_quote_service_line','forwarder_quote_cargo_category','forwarder_quote_base_price','forwarder_quote_transport_fee','forwarder_quote_route_template','forwarder_quote_route_template_segment','forwarder_quote_numeric_adjustment','forwarder_quote_numeric_adjustment_log','product_management_id_sequence','procurement_shipping_order_line'));
DROP TEMPORARY TABLE IF EXISTS `nuono_237_required_table_guard`; CREATE TEMPORARY TABLE `nuono_237_required_table_guard` (`invalid_count` BIGINT NOT NULL,CONSTRAINT `chk_237_required_tables` CHECK (`invalid_count` = 0)) ENGINE=MEMORY;
INSERT INTO `nuono_237_required_table_guard` VALUES (IF(@required_table_count = 11, 0, 1));
DROP TEMPORARY TABLE `nuono_237_required_table_guard`;
SET @adjustment_column_names := (SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment');
SET @adjustment_log_column_names := (SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment_log');
SET @adjustment_index_signature := (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name) ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment');
SET @adjustment_log_index_signature := (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name) ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment_log');
SET @adjustment_schema_fingerprint := (SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,ordinal_position,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(generation_expression,'')) ORDER BY table_name,ordinal_position SEPARATOR '\n'),256) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('forwarder_quote_numeric_adjustment','forwarder_quote_numeric_adjustment_log'));
SET @adjustment_rows_before := (SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment);
SET @adjustment_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,quote_version_id,target_type,target_id,field_name,original_value,adjusted_value,currency,reason,adjustment_status,created_by,updated_by,DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment);
SET @adjustment_log_rows_before := (SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment_log);
SET @adjustment_log_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,adjustment_id,quote_version_id,target_type,target_id,field_name,before_value,after_value,action_type,reason,operated_by,DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment_log);
SET @old_version_id := (SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001' ORDER BY id DESC LIMIT 1);
SET @new_version_id := (SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728' ORDER BY id DESC LIMIT 1);
SET @expected_source_raw_category_hash := 'a8ea877d8cc8fdbd249c2ea716f9cea0316b031c9d104419bb44f34e056290cf'; SET @expected_source_raw_price_hash := '2be8542906f265bc3cdcf60c763f0fe949b51e15c5f843064a77481065e40029'; SET @expected_source_category_business_hash := '088dff7da968d51e58fea26398acf661e329218397fba05faa657a4768930e30'; SET @expected_source_price_business_hash := '902b6173f5ee366a03a79f282777a67579ab8262598bdab89e588533cfd19ff1'; SET @expected_source_fee_business_hash := '74ff49fbd0863e298bbb9244a8db8c2429e12ce84d9dbf7dd7ac2a8df9e832f8';
SET @source_raw_category_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_raw_price_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_category_business_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_price_business_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,pricing_model,currency,CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1540.0000 WHEN '021' THEN 1900.0000 WHEN '022' THEN 2040.0000 WHEN '023' THEN 2290.0000 ELSE unit_price END,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_fee_business_hash_before := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_transport_fee WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_identity_exact_before := (SELECT COUNT(*) FROM forwarder_quote_version WHERE id=@old_version_id AND forwarder_id=900002 AND bundle_id=901002 AND status='PUBLISHED')=1 AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' AND forwarder_code='YT' AND country='沙特' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND transport_mode='SEA' AND business_type='B2B大货' AND delivery_scope='海运双清包税+FBN送仓' AND origin_warehouse IS NULL AND departure_frequency IS NULL AND transit_time_text IS NULL AND transit_days_min IS NULL AND transit_days_max IS NULL AND active_for_mvp=b'1')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH' AND forwarder_code='YT' AND route_name='义特沙特海运双清包税 + FBN利雅得送仓' AND country='沙特' AND site_code='SA' AND transport_mode='SEA' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND route_scope='报价服务线已包含送仓' AND active_for_purchase_order=b'1')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_no=1 AND segment_role='HEADHAUL' AND cost_policy='ESTIMATE' AND required=b'1' AND HEX(display_name)='C3A6C2B5C2B7C3A8C2BFC290C3A5C28FC592C3A6C2B8E280A6C3A5C592E280A6C3A7C2A8C5BDC3A5C290C2ABC3A9E282ACC281C3A4C2BBE2809C')=1 AND (SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' AND unit_price=CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1190 WHEN '021' THEN 1640 WHEN '022' THEN 1740 WHEN '023' THEN 2140 ELSE -1 END)=4 AND (SELECT COUNT(*) FROM forwarder_quote_base_price price JOIN forwarder_quote_numeric_adjustment adjustment ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id AND adjustment.field_name='unit_price' AND adjustment.adjustment_status='ACTIVE' AND adjustment.original_value=price.unit_price AND adjustment.adjusted_value=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900 WHEN '022' THEN 2040 WHEN '023' THEN 2290 ELSE -1 END WHERE price.quote_version_id=@old_version_id AND price.service_code='YT-SAU-SEA-FBN-RUH' AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023'))=4;
SET @source_adjustment_mapping_exact_before := (SELECT COUNT(*) FROM forwarder_quote_base_price price JOIN forwarder_quote_numeric_adjustment adjustment ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id AND adjustment.field_name='unit_price' AND adjustment.currency='RMB' AND adjustment.adjustment_status='ACTIVE' AND adjustment.original_value=price.unit_price AND adjustment.adjusted_value=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900 WHEN '022' THEN 2040 WHEN '023' THEN 2290 ELSE -1 END WHERE price.quote_version_id=@old_version_id AND price.service_code='YT-SAU-SEA-FBN-RUH' AND price.id=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 912020 WHEN '021' THEN 912021 WHEN '022' THEN 912022 WHEN '023' THEN 912023 ELSE -1 END)=4;
SET @source_contract_exact_before := @source_raw_category_hash_before=@expected_source_raw_category_hash AND @source_raw_price_hash_before=@expected_source_raw_price_hash AND @source_category_business_hash_before=@expected_source_category_business_hash AND @source_price_business_hash_before=@expected_source_price_business_hash AND @source_fee_business_hash_before=@expected_source_fee_business_hash AND @source_identity_exact_before AND @source_adjustment_mapping_exact_before;
SET @source_exact := (SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001')=1 AND (SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001' AND id=@old_version_id AND status='PUBLISHED')=1 AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH')=1 AND (SELECT COUNT(*) FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH')=10 AND (SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH')=10 AND (SELECT COUNT(*) FROM forwarder_quote_transport_fee WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL')=1;
SET @target_version_count := (SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728');
SET @target_artifact_count := @target_version_count+(SELECT COUNT(*) FROM forwarder_quote_service_line WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+(SELECT COUNT(*) FROM forwarder_quote_cargo_category WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+(SELECT COUNT(*) FROM forwarder_quote_base_price WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+(SELECT COUNT(*) FROM forwarder_quote_transport_fee WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728');
SET @target_final_before := @target_version_count=1 AND @new_version_id IS NOT NULL AND @target_artifact_count=23 AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728')=1 AND (SELECT COUNT(*) FROM forwarder_quote_cargo_category WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728')=10 AND (SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728')=10 AND (SELECT COUNT(*) FROM forwarder_quote_transport_fee WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH' AND quote_version_id=@new_version_id AND quote_version_code='YT-SAU-20260728')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL' AND service_code='YT-SAU-SEA-FBN-RUH-20260728')=1;
SET @legacy_contract_exact := @adjustment_rows_before=4 AND @adjustment_log_rows_before=4
    AND @adjustment_schema_fingerprint='9cf247aea2f146265c979b3467bcfb6e41a2a864f7da226ef4789171b82bd444'
    AND @adjustment_hash_before='025a8cfa78920deaff035819431e45742a6ee2830f1c1e010ef36383f5c82db2'
    AND @adjustment_log_hash_before='83caf487c8953f0eff04ef719e5482a65158a4e3773f1176802838baf9e03245'
    AND @adjustment_column_names='id,quote_version_id,target_type,target_id,field_name,original_value,adjusted_value,currency,reason,adjustment_status,created_by,updated_by,gmt_create,gmt_updated'
    AND @adjustment_log_column_names='id,adjustment_id,quote_version_id,target_type,target_id,field_name,before_value,after_value,action_type,reason,operated_by,gmt_create'
    AND @adjustment_index_signature='idx_fq_numeric_adjustment_version:1:1:quote_version_id,PRIMARY:0:1:id,uk_fq_numeric_adjustment_current:0:1:target_type,uk_fq_numeric_adjustment_current:0:2:target_id,uk_fq_numeric_adjustment_current:0:3:field_name,uk_fq_numeric_adjustment_current:0:4:adjustment_status'
    AND @adjustment_log_index_signature='idx_fq_numeric_adjustment_log_target:1:1:target_type,idx_fq_numeric_adjustment_log_target:1:2:target_id,idx_fq_numeric_adjustment_log_target:1:3:field_name,idx_fq_numeric_adjustment_log_version:1:1:quote_version_id,PRIMARY:0:1:id';
SET @fence_trigger_count := (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN ('trg_fq_numeric_adjustment_retired_bi','trg_fq_numeric_adjustment_retired_bu','trg_fq_numeric_adjustment_retired_bd','trg_fq_numeric_adjustment_log_retired_bi','trg_fq_numeric_adjustment_log_retired_bu','trg_fq_numeric_adjustment_log_retired_bd'));
SET @fence_trigger_exact_count := (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND action_timing='BEFORE' AND action_orientation='ROW' AND LOWER(REGEXP_REPLACE(action_statement,'[[:space:]]+',' ')) REGEXP '^signal sqlstate( value)? ''45000'' set message_text = ''legacy numeric adjustment writer fenced by migration 237''$' AND ((trigger_name LIKE '%_bi' AND event_manipulation='INSERT') OR (trigger_name LIKE '%_bu' AND event_manipulation='UPDATE') OR (trigger_name LIKE '%_bd' AND event_manipulation='DELETE')) AND ((trigger_name LIKE 'trg_fq_numeric_adjustment_log_%' AND event_object_table='forwarder_quote_numeric_adjustment_log') OR (trigger_name LIKE 'trg_fq_numeric_adjustment_retired_%' AND event_object_table='forwarder_quote_numeric_adjustment')) AND trigger_name IN ('trg_fq_numeric_adjustment_retired_bi','trg_fq_numeric_adjustment_retired_bu','trg_fq_numeric_adjustment_retired_bd','trg_fq_numeric_adjustment_log_retired_bi','trg_fq_numeric_adjustment_log_retired_bu','trg_fq_numeric_adjustment_log_retired_bd'));
SET @fence_trigger_subset_exact := @fence_trigger_count BETWEEN 0 AND 6 AND @fence_trigger_exact_count=@fence_trigger_count;
SET @base_state_valid := @source_exact AND @source_contract_exact_before AND @legacy_contract_exact AND @fence_trigger_subset_exact AND (
    (@target_artifact_count=0
      AND (SELECT COUNT(*) FROM forwarder_quote_version WHERE id=@old_version_id AND effective_to IS NULL)=1
      AND (SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH'
        AND quote_version_id=@old_version_id)=1
      AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH'
        AND segment_role='HEADHAUL' AND service_code='YT-SAU-SEA-FBN-RUH')=1)
    OR @target_final_before
);
DROP TEMPORARY TABLE IF EXISTS `nuono_237_base_state_guard`; CREATE TEMPORARY TABLE `nuono_237_base_state_guard` (`invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_237_base_state` CHECK (`invalid_count`=0)) ENGINE=MEMORY;
INSERT INTO `nuono_237_base_state_guard` VALUES (IF(@base_state_valid,0,1));
DROP TEMPORARY TABLE `nuono_237_base_state_guard`;
SET @eligibility_object_count := (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility');
SET @eligibility_table_count := (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility'
      AND table_type='BASE TABLE' AND UPPER(engine)='INNODB' AND table_collation='utf8mb4_unicode_ci');
SET @eligibility_column_count := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility');
SET @eligibility_column_signature := (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable) ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility');
SET @eligibility_index_signature := (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
    ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility');
SET @eligibility_check_signature := (SELECT GROUP_CONCAT(CONCAT(constraint_name,':',enforced) ORDER BY constraint_name SEPARATOR ',') FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND constraint_type='CHECK');
SET @eligibility_default_exact := @eligibility_column_count=20 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_default IS NULL)=16 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='version' AND column_default='1' AND extra='')=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='is_deleted' AND column_default='b''0''' AND extra='')=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='gmt_create' AND UPPER(column_default)='CURRENT_TIMESTAMP' AND UPPER(extra)='DEFAULT_GENERATED')=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='gmt_updated' AND UPPER(column_default)='CURRENT_TIMESTAMP' AND UPPER(extra)='DEFAULT_GENERATED ON UPDATE CURRENT_TIMESTAMP')=1;
SET @eligibility_check_clause_exact := (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_status' AND REGEXP_REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),'_utf8mb4',''),'[()[:space:]]+','')='casteligibility_statusasbinaryincast''inquiry_required''asbinary,cast''unsupported''asbinary')=1 AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_version' AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')='version>0')=1 AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_effective' AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')='effective_toisnulloreffective_to>=effective_from')=1 AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_scope_codes' AND REGEXP_REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),'_utf8mb4',''),'[()[:space:]]+','')='castsite_codeasbinary=castuppertrimsite_codeasbinaryandoctet_lengthtrimsite_code>0andcastforwarder_codeasbinary=castuppertrimforwarder_codeasbinaryandoctet_lengthtrimforwarder_code>0andcasttransport_modeasbinary=castuppertrimtransport_modeasbinaryandcasttransport_modeasbinaryincast''air''asbinary,cast''sea''asbinary')=1;
SET @eligibility_generated_exact := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='active_scope_slot' AND REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(generation_expression),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),CONCAT(CHAR(92),'0'),'0'),'_utf8mb4',''),'((_binary|b)?''0''|0x00|0b0)','0'),'[()[:space:]]+',''),'charsetutf8mb4','')='casewhenis_deleted=0andeffective_toisnullthenconcatcastowner_user_idaschar,'':'',castproduct_variant_idaschar,'':'',uppertrimsite_code,'':'',uppertrimforwarder_code,'':'',uppertrimtransport_modeelsenullend')=1;
SET @eligibility_existing_exact := @eligibility_table_count=1 AND @eligibility_column_count=20
    AND @eligibility_column_signature='id:bigint:NO,owner_user_id:bigint:NO,product_master_id:bigint:YES,product_variant_id:bigint:NO,logical_store_id:bigint:YES,source_store_code:varchar(100):YES,partner_sku:varchar(100):YES,site_code:varchar(20):NO,forwarder_code:varchar(80):NO,transport_mode:varchar(20):NO,eligibility_status:varchar(40):NO,effective_from:date:NO,effective_to:date:YES,version:int:NO,is_deleted:bit(1):NO,active_scope_slot:varchar(255):YES,created_by:bigint:YES,updated_by:bigint:YES,gmt_create:datetime:NO,gmt_updated:datetime:NO'
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='product_forwarder_transport_eligibility' AND column_name='active_scope_slot'
      AND data_type='varchar' AND character_maximum_length=255 AND is_nullable='YES'
      AND UPPER(extra)='STORED GENERATED' AND generation_expression LIKE '%owner_user_id%'
      AND generation_expression LIKE '%product_variant_id%' AND generation_expression LIKE '%forwarder_code%')=1
    AND @eligibility_index_signature='idx_pfte_effective:1:1:effective_from,idx_pfte_effective:1:2:effective_to,idx_pfte_forwarder_scope:1:1:owner_user_id,idx_pfte_forwarder_scope:1:2:site_code,idx_pfte_forwarder_scope:1:3:forwarder_code,idx_pfte_forwarder_scope:1:4:transport_mode,idx_pfte_forwarder_scope:1:5:is_deleted,idx_pfte_owner_variant:1:1:owner_user_id,idx_pfte_owner_variant:1:2:product_variant_id,idx_pfte_owner_variant:1:3:is_deleted,PRIMARY:0:1:id,uk_pfte_active_scope:0:1:active_scope_slot';
SET @eligibility_existing_exact := @eligibility_existing_exact AND @eligibility_check_signature='chk_pfte_effective:YES,chk_pfte_scope_codes:YES,chk_pfte_status:YES,chk_pfte_version:YES';
SET @eligibility_existing_exact := @eligibility_existing_exact AND @eligibility_default_exact AND @eligibility_check_clause_exact AND @eligibility_generated_exact;
SET @snapshot_column_count := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='procurement_shipping_order_line' AND column_name='eligibility_status_snapshot');
SET @snapshot_column_exact := (SELECT COUNT(*)=1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='procurement_shipping_order_line' AND column_name='eligibility_status_snapshot'
    AND data_type='varchar' AND character_maximum_length=40 AND is_nullable='YES' AND column_default IS NULL
    AND extra='' AND generation_expression='');
SET @snapshot_check_count := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='procurement_shipping_order_line' AND constraint_name='chk_shipping_line_eligibility_snapshot' AND constraint_type='CHECK');
SET @snapshot_check_exact := @snapshot_check_count=1 AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='procurement_shipping_order_line' AND constraint_name='chk_shipping_line_eligibility_snapshot' AND constraint_type='CHECK' AND enforced='YES')=1 AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_shipping_line_eligibility_snapshot' AND REGEXP_REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),'_utf8mb4',''),'[()[:space:]]+','')='eligibility_status_snapshotisnullorcasteligibility_status_snapshotasbinaryincast''supported''asbinary,cast''inquiry_required''asbinary,cast''unsupported''asbinary')=1;
DROP TEMPORARY TABLE IF EXISTS `nuono_237_eligibility_state_guard`; CREATE TEMPORARY TABLE `nuono_237_eligibility_state_guard` (`invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_237_eligibility_state` CHECK (`invalid_count`=0)) ENGINE=MEMORY;
INSERT INTO `nuono_237_eligibility_state_guard` VALUES (IF(
    (@eligibility_object_count=0 OR @eligibility_existing_exact)
      AND ((@snapshot_column_count=0 AND @snapshot_check_count=0)
        OR (@snapshot_column_exact AND @snapshot_check_exact)),0,1));
DROP TEMPORARY TABLE `nuono_237_eligibility_state_guard`;
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_retired_bi` BEFORE INSERT ON `forwarder_quote_numeric_adjustment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_retired_bu` BEFORE UPDATE ON `forwarder_quote_numeric_adjustment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_retired_bd` BEFORE DELETE ON `forwarder_quote_numeric_adjustment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_log_retired_bi` BEFORE INSERT ON `forwarder_quote_numeric_adjustment_log` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_log_retired_bu` BEFORE UPDATE ON `forwarder_quote_numeric_adjustment_log` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
CREATE TRIGGER IF NOT EXISTS `trg_fq_numeric_adjustment_log_retired_bd` BEFORE DELETE ON `forwarder_quote_numeric_adjustment_log` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237';
SET @fence_trigger_count_after := (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN ('trg_fq_numeric_adjustment_retired_bi','trg_fq_numeric_adjustment_retired_bu','trg_fq_numeric_adjustment_retired_bd','trg_fq_numeric_adjustment_log_retired_bi','trg_fq_numeric_adjustment_log_retired_bu','trg_fq_numeric_adjustment_log_retired_bd'));
START TRANSACTION;
SELECT id INTO @locked_old_version FROM forwarder_quote_version WHERE id=@old_version_id FOR UPDATE;
SELECT id FROM forwarder_quote_service_line WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' FOR UPDATE;
SELECT id FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' FOR UPDATE;
SELECT id FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' FOR UPDATE;
SELECT id FROM forwarder_quote_transport_fee WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' FOR UPDATE;
SELECT id FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH' FOR UPDATE;
SELECT id FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL' FOR UPDATE;
SELECT id FROM forwarder_quote_numeric_adjustment WHERE quote_version_id=@old_version_id ORDER BY id FOR UPDATE;
SELECT id FROM forwarder_quote_numeric_adjustment_log WHERE quote_version_id=@old_version_id ORDER BY id FOR UPDATE;
SET @source_category_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_price_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,pricing_model,currency,CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1540.0000 WHEN '021' THEN 1900.0000 WHEN '022' THEN 2040.0000 WHEN '023' THEN 2290.0000 ELSE unit_price END,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_fee_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_transport_fee WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_raw_category_hash_locked := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_raw_price_hash_locked := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH');
SET @source_identity_exact_locked := (SELECT COUNT(*) FROM forwarder_quote_version WHERE id=@old_version_id AND forwarder_id=900002 AND bundle_id=901002 AND status='PUBLISHED')=1 AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' AND forwarder_code='YT' AND country='沙特' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND transport_mode='SEA' AND business_type='B2B大货' AND delivery_scope='海运双清包税+FBN送仓' AND origin_warehouse IS NULL AND departure_frequency IS NULL AND transit_time_text IS NULL AND transit_days_min IS NULL AND transit_days_max IS NULL AND active_for_mvp=b'1')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH' AND forwarder_code='YT' AND route_name='义特沙特海运双清包税 + FBN利雅得送仓' AND country='沙特' AND site_code='SA' AND transport_mode='SEA' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND route_scope='报价服务线已包含送仓' AND active_for_purchase_order=b'1')=1 AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_no=1 AND segment_role='HEADHAUL' AND cost_policy='ESTIMATE' AND required=b'1' AND HEX(display_name)='C3A6C2B5C2B7C3A8C2BFC290C3A5C28FC592C3A6C2B8E280A6C3A5C592E280A6C3A7C2A8C5BDC3A5C290C2ABC3A9E282ACC281C3A4C2BBE2809C')=1 AND (SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH' AND unit_price=CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1190 WHEN '021' THEN 1640 WHEN '022' THEN 1740 WHEN '023' THEN 2140 ELSE -1 END)=4 AND (SELECT COUNT(*) FROM forwarder_quote_base_price price JOIN forwarder_quote_numeric_adjustment adjustment ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id AND adjustment.field_name='unit_price' AND adjustment.adjustment_status='ACTIVE' AND adjustment.original_value=price.unit_price AND adjustment.adjusted_value=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900 WHEN '022' THEN 2040 WHEN '023' THEN 2290 ELSE -1 END WHERE price.quote_version_id=@old_version_id AND price.service_code='YT-SAU-SEA-FBN-RUH' AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023'))=4;
SET @source_adjustment_mapping_exact_locked := (SELECT COUNT(*) FROM forwarder_quote_base_price price JOIN forwarder_quote_numeric_adjustment adjustment ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id AND adjustment.field_name='unit_price' AND adjustment.currency='RMB' AND adjustment.adjustment_status='ACTIVE' AND adjustment.original_value=price.unit_price AND adjustment.adjusted_value=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900 WHEN '022' THEN 2040 WHEN '023' THEN 2290 ELSE -1 END WHERE price.quote_version_id=@old_version_id AND price.service_code='YT-SAU-SEA-FBN-RUH' AND price.id=CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 912020 WHEN '021' THEN 912021 WHEN '022' THEN 912022 WHEN '023' THEN 912023 ELSE -1 END)=4;
SET @adjustment_hash_locked := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,quote_version_id,target_type,target_id,field_name,original_value,adjusted_value,currency,reason,adjustment_status,created_by,updated_by,DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment);
SET @adjustment_log_hash_locked := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,adjustment_id,quote_version_id,target_type,target_id,field_name,before_value,after_value,action_type,reason,operated_by,DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment_log);
DROP TEMPORARY TABLE IF EXISTS `nuono_237_locked_legacy_guard`; CREATE TEMPORARY TABLE `nuono_237_locked_legacy_guard` (`invalid_count` BIGINT NOT NULL,CONSTRAINT `chk_237_locked_legacy` CHECK (`invalid_count`=0)) ENGINE=MEMORY;
INSERT INTO `nuono_237_locked_legacy_guard` VALUES (IF((SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment)=4 AND (SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment_log)=4 AND @adjustment_hash_locked=@adjustment_hash_before AND @adjustment_log_hash_locked=@adjustment_log_hash_before AND @source_raw_category_hash_locked=@expected_source_raw_category_hash AND @source_raw_price_hash_locked=@expected_source_raw_price_hash AND @source_category_business_hash=@expected_source_category_business_hash AND @source_price_business_hash=@expected_source_price_business_hash AND @source_fee_business_hash=@expected_source_fee_business_hash AND @source_identity_exact_locked AND @source_adjustment_mapping_exact_locked,0,1)); DROP TEMPORARY TABLE `nuono_237_locked_legacy_guard`;
SELECT id INTO @locked_version_max FROM forwarder_quote_version ORDER BY id DESC LIMIT 1 FOR UPDATE;
SELECT id INTO @locked_service_max FROM forwarder_quote_service_line ORDER BY id DESC LIMIT 1 FOR UPDATE;
SELECT id INTO @locked_category_max FROM forwarder_quote_cargo_category ORDER BY id DESC LIMIT 1 FOR UPDATE;
SELECT id INTO @locked_price_max FROM forwarder_quote_base_price ORDER BY id DESC LIMIT 1 FOR UPDATE;
SELECT id INTO @locked_fee_max FROM forwarder_quote_transport_fee ORDER BY id DESC LIMIT 1 FOR UPDATE;
SET @new_version_id := COALESCE(@new_version_id,@locked_version_max+1);
SET @new_service_id := COALESCE((SELECT id FROM forwarder_quote_service_line
    WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728' LIMIT 1),@locked_service_max+1);

INSERT INTO forwarder_quote_version (id,forwarder_id,bundle_id,version_no,effective_from,effective_to,status,
    summary,created_by,updated_by,gmt_create,gmt_updated)
SELECT @new_version_id,forwarder_id,bundle_id,'YT-SAU-20260728','2026-07-28',NULL,'PUBLISHED',
    '义特通知：2026-07-28 起入仓执行新价；普货1540/CBM、小家电1900/CBM、灯具2040/CBM、一般敏感货2290/CBM。',
    307,307,NOW(),NOW() FROM forwarder_quote_version WHERE id=@old_version_id
    AND NOT EXISTS (SELECT 1 FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728');
UPDATE forwarder_quote_version SET effective_to='2026-07-27',updated_by=307,gmt_updated=NOW()
    WHERE id=@old_version_id AND (effective_to IS NULL OR effective_to>'2026-07-27');
INSERT INTO forwarder_quote_service_line (id,quote_version_id,quote_version_code,forwarder_code,service_code,
    service_name,country,target_platform,delivery_city,destination_node,transport_mode,business_type,delivery_scope,
    origin_warehouse,departure_frequency,transit_time_text,transit_days_min,transit_days_max,active_for_mvp,
    source_file_name,source_sheet_or_page,source_row_or_locator,source_type,remark,created_by,updated_by,gmt_create,gmt_updated)
SELECT @new_service_id,@new_version_id,'YT-SAU-20260728',forwarder_code,'YT-SAU-SEA-FBN-RUH-20260728',
    '义特沙特海运双清包税 + FBN利雅得送仓 20260728',country,target_platform,delivery_city,destination_node,
    transport_mode,business_type,delivery_scope,origin_warehouse,departure_frequency,transit_time_text,
    transit_days_min,transit_days_max,active_for_mvp,'义特2026-07-28调价通知','人工确认','自2026-07-28起入仓',
    'manual_quote_notice','正式报价版本；价格结果已按通知计算，不依赖数值调价覆盖。',307,307,NOW(),NOW()
    FROM forwarder_quote_service_line WHERE quote_version_id=@old_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH'
      AND NOT EXISTS (SELECT 1 FROM forwarder_quote_service_line
        WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728');
INSERT INTO forwarder_quote_cargo_category (id,quote_version_id,quote_version_code,forwarder_code,service_code,
    cargo_category_code,cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,
    product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority,
    source_file_name,source_sheet_or_page,source_row_or_locator,source_type,remark,created_by,updated_by,gmt_create,gmt_updated)
SELECT @locked_category_max+ROW_NUMBER() OVER (ORDER BY id),@new_version_id,'YT-SAU-20260728',forwarder_code,
    'YT-SAU-SEA-FBN-RUH-20260728',REPLACE(cargo_category_code,'YT-SAU-SEA-FBN-RUH','YT-SAU-SEA-FBN-RUH-20260728'),
    CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电'
      WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,
    source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,
    packing_policy,manual_confirm_required,match_priority,'义特2026-07-28调价通知','人工确认','自2026-07-28起入仓',
    'manual_quote_notice',remark,307,307,NOW(),NOW() FROM forwarder_quote_cargo_category old
    WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH'
      AND NOT EXISTS (SELECT 1 FROM forwarder_quote_cargo_category current_category
        WHERE current_category.cargo_category_code=REPLACE(old.cargo_category_code,
          'YT-SAU-SEA-FBN-RUH','YT-SAU-SEA-FBN-RUH-20260728'));
INSERT INTO forwarder_quote_base_price (id,price_rule_code,quote_version_id,quote_version_code,service_code,
    cargo_category_code,cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,
    volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,
    delivery_city,price_status,source_file_name,source_sheet_or_page,source_row_or_locator,source_type,remark,
    created_by,updated_by,gmt_create,gmt_updated)
SELECT @locked_price_max+ROW_NUMBER() OVER (ORDER BY id),CONCAT('PR-YT-20260728-',RIGHT(cargo_category_code,3)),
    @new_version_id,'YT-SAU-20260728','YT-SAU-SEA-FBN-RUH-20260728',
    REPLACE(cargo_category_code,'YT-SAU-SEA-FBN-RUH','YT-SAU-SEA-FBN-RUH-20260728'),
    CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电'
      WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END,
    pricing_model,currency,CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1540.0000 WHEN '021' THEN 1900.0000
      WHEN '022' THEN 2040.0000 WHEN '023' THEN 2290.0000 ELSE unit_price END,
    billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,
    rounding_rule,target_platform,delivery_city,price_status,'义特2026-07-28调价通知','人工确认','自2026-07-28起入仓',
    'manual_quote_notice',CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '由1190重新计算为1540 RMB/CBM。'
      WHEN '021' THEN '由1640重新计算为1900 RMB/CBM。' WHEN '022' THEN '由1740重新计算为2040 RMB/CBM。'
      WHEN '023' THEN '由2140重新计算为2290 RMB/CBM。' ELSE '沿用上一正式报价版本。' END,
    307,307,NOW(),NOW() FROM forwarder_quote_base_price old WHERE quote_version_id=@old_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH' AND NOT EXISTS (SELECT 1 FROM forwarder_quote_base_price current_price
        WHERE current_price.price_rule_code=CONCAT('PR-YT-20260728-',RIGHT(old.cargo_category_code,3)));
INSERT INTO forwarder_quote_transport_fee (id,fee_rule_code,quote_version_id,quote_version_code,service_code,fee_name,
    fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,
    min_charge,min_billable_unit,rounding_rule,included_in_base_price,source_file_name,source_sheet_or_page,
    source_row_or_locator,source_type,remark,created_by,updated_by,gmt_create,gmt_updated)
SELECT @locked_fee_max+ROW_NUMBER() OVER (ORDER BY id),CONCAT('FEE-YT-20260728-',RIGHT(fee_rule_code,4)),
    @new_version_id,'YT-SAU-20260728','YT-SAU-SEA-FBN-RUH-20260728',fee_name,fee_type,target_platform,delivery_city,
    trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,
    rounding_rule,included_in_base_price,'义特2026-07-28调价通知','人工确认','自2026-07-28起入仓',
    'manual_quote_notice','沿用上一正式报价版本。',307,307,NOW(),NOW() FROM forwarder_quote_transport_fee old
    WHERE quote_version_id=@old_version_id AND service_code='YT-SAU-SEA-FBN-RUH'
      AND NOT EXISTS (SELECT 1 FROM forwarder_quote_transport_fee current_fee
        WHERE current_fee.fee_rule_code=CONCAT('FEE-YT-20260728-',RIGHT(old.fee_rule_code,4)));
UPDATE forwarder_quote_route_template SET quote_version_id=@new_version_id,quote_version_code='YT-SAU-20260728',
    source_file_name='义特2026-07-28调价通知',source_sheet_or_page='人工确认',source_row_or_locator='自2026-07-28起入仓',
    source_type='manual_quote_notice',remark='当前路线指向2026-07-28生效的正式报价版本。',updated_by=307,gmt_updated=NOW()
    WHERE route_code='YT-SAU-SEA-FBN-RUH';
UPDATE forwarder_quote_route_template_segment SET service_code='YT-SAU-SEA-FBN-RUH-20260728',
    remark='HEADHAUL使用2026-07-28生效的正式报价服务线。',updated_by=307,gmt_updated=NOW()
    WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL';
COMMIT;
CREATE TABLE IF NOT EXISTS `product_forwarder_transport_eligibility` (
    `id` BIGINT NOT NULL, `owner_user_id` BIGINT NOT NULL, `product_master_id` BIGINT DEFAULT NULL,
    `product_variant_id` BIGINT NOT NULL, `logical_store_id` BIGINT DEFAULT NULL,
    `source_store_code` VARCHAR(100) DEFAULT NULL, `partner_sku` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL, `forwarder_code` VARCHAR(80) NOT NULL,
    `transport_mode` VARCHAR(20) NOT NULL, `eligibility_status` VARCHAR(40) NOT NULL,
    `effective_from` DATE NOT NULL, `effective_to` DATE DEFAULT NULL, `version` INT NOT NULL DEFAULT 1,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0', `active_scope_slot` VARCHAR(255) GENERATED ALWAYS AS
      (CASE WHEN `is_deleted`=b'0' AND `effective_to` IS NULL THEN CONCAT(CAST(`owner_user_id` AS CHAR),':',
       CAST(`product_variant_id` AS CHAR),':',UPPER(TRIM(`site_code`)),':',UPPER(TRIM(`forwarder_code`)),':',
       UPPER(TRIM(`transport_mode`))) ELSE NULL END) STORED,
    `created_by` BIGINT DEFAULT NULL, `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_pfte_active_scope` (`active_scope_slot`),
    KEY `idx_pfte_owner_variant` (`owner_user_id`,`product_variant_id`,`is_deleted`),
    KEY `idx_pfte_forwarder_scope` (`owner_user_id`,`site_code`,`forwarder_code`,`transport_mode`,`is_deleted`),
    KEY `idx_pfte_effective` (`effective_from`,`effective_to`),
    CONSTRAINT `chk_pfte_status` CHECK (CAST(`eligibility_status` AS BINARY) IN (CAST('INQUIRY_REQUIRED' AS BINARY),CAST('UNSUPPORTED' AS BINARY))),
    CONSTRAINT `chk_pfte_version` CHECK (`version`>0),
    CONSTRAINT `chk_pfte_effective` CHECK (`effective_to` IS NULL OR `effective_to`>=`effective_from`),
    CONSTRAINT `chk_pfte_scope_codes` CHECK (CAST(`site_code` AS BINARY)=CAST(UPPER(TRIM(`site_code`)) AS BINARY) AND OCTET_LENGTH(TRIM(`site_code`))>0 AND CAST(`forwarder_code` AS BINARY)=CAST(UPPER(TRIM(`forwarder_code`)) AS BINARY) AND OCTET_LENGTH(TRIM(`forwarder_code`))>0 AND CAST(`transport_mode` AS BINARY)=CAST(UPPER(TRIM(`transport_mode`)) AS BINARY) AND CAST(`transport_mode` AS BINARY) IN (CAST('AIR' AS BINARY),CAST('SEA' AS BINARY)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO product_management_id_sequence (sequence_name,next_id,gmt_create,gmt_updated)
SELECT 'product_forwarder_transport_eligibility',GREATEST(COALESCE(MAX(id),370000),370000),NOW(),NOW()
    FROM product_forwarder_transport_eligibility
ON DUPLICATE KEY UPDATE next_id=GREATEST(next_id,VALUES(next_id)),gmt_updated=NOW();
SET @snapshot_sql := IF(@snapshot_column_count=0,
    'ALTER TABLE `procurement_shipping_order_line` ADD COLUMN `eligibility_status_snapshot` VARCHAR(40) DEFAULT NULL AFTER `quote_line_id`, ADD CONSTRAINT `chk_shipping_line_eligibility_snapshot` CHECK (`eligibility_status_snapshot` IS NULL OR CAST(`eligibility_status_snapshot` AS BINARY) IN (CAST(''SUPPORTED'' AS BINARY),CAST(''INQUIRY_REQUIRED'' AS BINARY),CAST(''UNSUPPORTED'' AS BINARY)))','DO 0');
PREPARE snapshot_stmt FROM @snapshot_sql; EXECUTE snapshot_stmt; DEALLOCATE PREPARE snapshot_stmt;
SET @new_version_id := (SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728' LIMIT 1);
SET @adjustment_schema_after := (SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,ordinal_position,column_name,
    column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(generation_expression,''))
    ORDER BY table_name,ordinal_position SEPARATOR '\n'),256) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name IN
      ('forwarder_quote_numeric_adjustment','forwarder_quote_numeric_adjustment_log'));
SET @adjustment_hash_after := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,quote_version_id,target_type,target_id,
    field_name,original_value,adjusted_value,currency,reason,adjustment_status,created_by,updated_by,
    DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR)
    ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment);
SET @adjustment_log_hash_after := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,adjustment_id,quote_version_id,
    target_type,target_id,field_name,before_value,after_value,action_type,reason,operated_by,
    DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256)
    FROM forwarder_quote_numeric_adjustment_log);
SET @target_category_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728');
SET @target_price_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728');
SET @target_fee_business_hash := (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_transport_fee WHERE quote_version_id=@new_version_id AND service_code='YT-SAU-SEA-FBN-RUH-20260728');
SET @final_invalid_count :=
    ((SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728'
      AND effective_from='2026-07-28' AND effective_to IS NULL AND status='PUBLISHED')<>1)
    + ((SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@new_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH-20260728' AND forwarder_code='YT' AND country='沙特' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND transport_mode='SEA' AND business_type='B2B大货' AND delivery_scope='海运双清包税+FBN送仓' AND active_for_mvp=b'1')<>1)
    + ((SELECT COUNT(*) FROM forwarder_quote_cargo_category WHERE quote_version_id=@new_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH-20260728')<>10)
    + ((SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@new_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH-20260728')<>10)
    + ((SELECT COUNT(*) FROM forwarder_quote_transport_fee WHERE quote_version_id=@new_version_id
      AND service_code='YT-SAU-SEA-FBN-RUH-20260728')<>1)
    + ((SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=@new_version_id AND
      unit_price=CASE RIGHT(cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900
        WHEN '022' THEN 2040 WHEN '023' THEN 2290 ELSE -1 END)<>4)
    + ((SELECT COUNT(*) FROM forwarder_quote_route_template WHERE route_code='YT-SAU-SEA-FBN-RUH'
      AND quote_version_id=@new_version_id AND quote_version_code='YT-SAU-20260728' AND forwarder_code='YT' AND country='沙特' AND site_code='SA' AND transport_mode='SEA' AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓' AND active_for_purchase_order=b'1')<>1)
    + ((SELECT COUNT(*) FROM forwarder_quote_route_template_segment WHERE route_code='YT-SAU-SEA-FBN-RUH'
      AND segment_no=1 AND segment_role='HEADHAUL' AND service_code='YT-SAU-SEA-FBN-RUH-20260728' AND cost_policy='ESTIMATE' AND required=b'1' AND HEX(display_name)='C3A6C2B5C2B7C3A8C2BFC290C3A5C28FC592C3A6C2B8E280A6C3A5C592E280A6C3A7C2A8C5BDC3A5C290C2ABC3A9E282ACC281C3A4C2BBE2809C')<>1)
    + ((SELECT COUNT(*) FROM forwarder_quote_base_price price LEFT JOIN forwarder_quote_numeric_adjustment adjustment
      ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id
      AND adjustment.field_name='unit_price' AND adjustment.adjustment_status='ACTIVE'
      WHERE price.quote_version_id=@new_version_id AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023')
        AND adjustment.id IS NULL AND COALESCE(adjustment.adjusted_value,price.unit_price)=
          CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540 WHEN '021' THEN 1900
            WHEN '022' THEN 2040 WHEN '023' THEN 2290 END)<>4)
    + (@adjustment_schema_after<>@adjustment_schema_fingerprint)
    + (@adjustment_hash_after<>@adjustment_hash_before)
    + (@adjustment_log_hash_after<>@adjustment_log_hash_before)
    + (@target_category_business_hash<>@source_category_business_hash)
    + (@target_price_business_hash<>@source_price_business_hash)
    + (@target_fee_business_hash<>@source_fee_business_hash)
    + (@fence_trigger_count_after<>6)
    + ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name='product_forwarder_transport_eligibility' AND table_type='BASE TABLE' AND UPPER(engine)='INNODB' AND table_collation='utf8mb4_unicode_ci')<>1)
    + ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='procurement_shipping_order_line' AND column_name='eligibility_status_snapshot'
      AND data_type='varchar' AND character_maximum_length=40 AND is_nullable='YES' AND column_default IS NULL)<>1);
DROP TEMPORARY TABLE IF EXISTS `nuono_237_final_guard`; CREATE TEMPORARY TABLE `nuono_237_final_guard` (`invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_237_final` CHECK (`invalid_count`=0)) ENGINE=MEMORY;
INSERT INTO `nuono_237_final_guard` VALUES (@final_invalid_count);
DROP TEMPORARY TABLE `nuono_237_final_guard`;
