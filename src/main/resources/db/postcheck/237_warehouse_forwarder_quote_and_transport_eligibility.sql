SELECT /*+ SET_VAR(group_concat_max_len=1048576) */ IF(
    (SELECT COUNT(*) FROM forwarder_quote_version
      WHERE forwarder_id=900002 AND bundle_id=901002 AND version_no='YT-SAU-20260728' AND effective_from='2026-07-28'
        AND effective_to IS NULL AND status='PUBLISHED')=1
    AND (SELECT COUNT(*) FROM forwarder_quote_version
      WHERE version_no='YT-SAU-UNDATED-001' AND effective_to='2026-07-27')=1
    AND (SELECT COUNT(*) FROM forwarder_quote_service_line service
      JOIN forwarder_quote_version version ON version.id=service.quote_version_id
      WHERE version.version_no='YT-SAU-20260728'
        AND service.quote_version_code='YT-SAU-20260728' AND service.service_code='YT-SAU-SEA-FBN-RUH-20260728' AND service.forwarder_code='YT'
        AND service.country='沙特' AND service.target_platform='FBN' AND service.delivery_city='利雅得/RUH'
        AND service.destination_node='FBN利雅得仓' AND service.transport_mode='SEA'
        AND service.business_type='B2B大货' AND service.delivery_scope='海运双清包税+FBN送仓'
        AND service.origin_warehouse IS NULL AND service.departure_frequency IS NULL
        AND service.transit_time_text IS NULL AND service.transit_days_min IS NULL AND service.transit_days_max IS NULL
        AND service.active_for_mvp=b'1')=1
    AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728'))=1
    AND (SELECT COUNT(*) FROM forwarder_quote_cargo_category category_row
      JOIN forwarder_quote_version version ON version.id=category_row.quote_version_id
      WHERE version.version_no='YT-SAU-20260728'
        AND category_row.service_code='YT-SAU-SEA-FBN-RUH-20260728')=10
    AND (SELECT COUNT(*) FROM forwarder_quote_cargo_category WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728'))=10
    AND (SELECT COUNT(*) FROM forwarder_quote_base_price price
      JOIN forwarder_quote_version version ON version.id=price.quote_version_id
      WHERE version.version_no='YT-SAU-20260728'
        AND price.service_code='YT-SAU-SEA-FBN-RUH-20260728')=10
    AND (SELECT COUNT(*) FROM forwarder_quote_base_price WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728'))=10
    AND (SELECT COUNT(*) FROM forwarder_quote_transport_fee fee
      JOIN forwarder_quote_version version ON version.id=fee.quote_version_id
      WHERE version.version_no='YT-SAU-20260728'
        AND fee.service_code='YT-SAU-SEA-FBN-RUH-20260728')=1
    AND (SELECT COUNT(*) FROM forwarder_quote_transport_fee WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728'))=1
    AND (SELECT COUNT(*) FROM forwarder_quote_base_price price
      JOIN forwarder_quote_version version ON version.id=price.quote_version_id
      WHERE version.version_no='YT-SAU-20260728'
        AND price.unit_price=CASE RIGHT(price.cargo_category_code,3)
          WHEN '020' THEN 1540 WHEN '021' THEN 1900 WHEN '022' THEN 2040
          WHEN '023' THEN 2290 ELSE -1 END)=4
    AND NOT EXISTS (SELECT 1 FROM forwarder_quote_route_template
      WHERE route_code='YT-SAU-SEA-FBN-RUH'
        AND quote_version_code='YT-SAU-UNDATED-001')
    AND NOT EXISTS (SELECT 1 FROM forwarder_quote_route_template_segment
      WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL'
        AND service_code='YT-SAU-SEA-FBN-RUH')
    AND (SELECT COUNT(*) FROM forwarder_quote_route_template
      WHERE route_code='YT-SAU-SEA-FBN-RUH' AND quote_version_code='YT-SAU-20260728'
        AND forwarder_code='YT' AND country='沙特' AND site_code='SA' AND transport_mode='SEA'
        AND target_platform='FBN' AND delivery_city='利雅得/RUH' AND destination_node='FBN利雅得仓'
        AND active_for_purchase_order=b'1')=1
    AND (SELECT COUNT(*) FROM forwarder_quote_route_template_segment
      WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_no=1 AND segment_role='HEADHAUL'
        AND service_code='YT-SAU-SEA-FBN-RUH-20260728' AND cost_policy='ESTIMATE' AND required=b'1'
        AND HEX(display_name)='C3A6C2B5C2B7C3A8C2BFC290C3A5C28FC592C3A6C2B8E280A6C3A5C592E280A6C3A7C2A8C5BDC3A5C290C2ABC3A9E282ACC281C3A4C2BBE2809C')=1
    AND (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
        AND table_name IN ('forwarder_quote_numeric_adjustment',
          'forwarder_quote_numeric_adjustment_log'))=2
    AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('forwarder_quote_numeric_adjustment','forwarder_quote_numeric_adjustment_log'))=6
    AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND action_timing='BEFORE' AND action_orientation='ROW' AND action_order=1 AND LOWER(REGEXP_REPLACE(action_statement,'[[:space:]]+',' ')) REGEXP '^signal sqlstate( value)? ''45000'' set message_text[[:space:]]*=[[:space:]]*''legacy numeric adjustment writer fenced by migration 237''$' AND ((trigger_name='trg_fq_numeric_adjustment_retired_bi' AND event_object_table='forwarder_quote_numeric_adjustment' AND event_manipulation='INSERT') OR (trigger_name='trg_fq_numeric_adjustment_retired_bu' AND event_object_table='forwarder_quote_numeric_adjustment' AND event_manipulation='UPDATE') OR (trigger_name='trg_fq_numeric_adjustment_retired_bd' AND event_object_table='forwarder_quote_numeric_adjustment' AND event_manipulation='DELETE') OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bi' AND event_object_table='forwarder_quote_numeric_adjustment_log' AND event_manipulation='INSERT') OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bu' AND event_object_table='forwarder_quote_numeric_adjustment_log' AND event_manipulation='UPDATE') OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bd' AND event_object_table='forwarder_quote_numeric_adjustment_log' AND event_manipulation='DELETE')))=6
    AND (SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,ordinal_position,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(generation_expression,'')) ORDER BY table_name,ordinal_position SEPARATOR '\n'),256) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('forwarder_quote_numeric_adjustment','forwarder_quote_numeric_adjustment_log'))='9cf247aea2f146265c979b3467bcfb6e41a2a864f7da226ef4789171b82bd444'
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment')
      ='id:bigint,quote_version_id:bigint,target_type:varchar(60),target_id:bigint,field_name:varchar(80),original_value:decimal(12,4),adjusted_value:decimal(12,4),currency:varchar(20),reason:varchar(500),adjustment_status:varchar(40),created_by:bigint,updated_by:bigint,gmt_create:datetime,gmt_updated:datetime'
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment_log')
      ='id:bigint,adjustment_id:bigint,quote_version_id:bigint,target_type:varchar(60),target_id:bigint,field_name:varchar(80),before_value:decimal(12,4),after_value:decimal(12,4),action_type:varchar(60),reason:varchar(500),operated_by:bigint,gmt_create:datetime'
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment')
      ='idx_fq_numeric_adjustment_version:1:1:quote_version_id,PRIMARY:0:1:id,uk_fq_numeric_adjustment_current:0:1:target_type,uk_fq_numeric_adjustment_current:0:2:target_id,uk_fq_numeric_adjustment_current:0:3:field_name,uk_fq_numeric_adjustment_current:0:4:adjustment_status'
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='forwarder_quote_numeric_adjustment_log')
      ='idx_fq_numeric_adjustment_log_target:1:1:target_type,idx_fq_numeric_adjustment_log_target:1:2:target_id,idx_fq_numeric_adjustment_log_target:1:3:field_name,idx_fq_numeric_adjustment_log_version:1:1:quote_version_id,PRIMARY:0:1:id'
    AND (SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment)=4
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,quote_version_id,target_type,target_id,
      field_name,original_value,adjusted_value,currency,reason,adjustment_status,created_by,updated_by,
      DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR)
      ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_numeric_adjustment)
      ='025a8cfa78920deaff035819431e45742a6ee2830f1c1e010ef36383f5c82db2'
    AND (SELECT COUNT(*) FROM forwarder_quote_numeric_adjustment_log)=4
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,adjustment_id,quote_version_id,target_type,
      target_id,field_name,before_value,after_value,action_type,reason,operated_by,
      DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id SEPARATOR '\n'),256)
      FROM forwarder_quote_numeric_adjustment_log)
      ='83caf487c8953f0eff04ef719e5482a65158a4e3773f1176802838baf9e03245'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001') AND service_code='YT-SAU-SEA-FBN-RUH')='a8ea877d8cc8fdbd249c2ea716f9cea0316b031c9d104419bb44f34e056290cf'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001') AND service_code='YT-SAU-SEA-FBN-RUH')='2be8542906f265bc3cdcf60c763f0fe949b51e15c5f843064a77481065e40029'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_transport_fee WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-UNDATED-001') AND service_code='YT-SAU-SEA-FBN-RUH')='74ff49fbd0863e298bbb9244a8db8c2429e12ce84d9dbf7dd7ac2a8df9e832f8'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,source_category_name,category_level_1,category_level_2,product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_cargo_category WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728') AND service_code='YT-SAU-SEA-FBN-RUH-20260728')='088dff7da968d51e58fea26398acf661e329218397fba05faa657a4768930e30'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_base_price WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728') AND service_code='YT-SAU-SEA-FBN-RUH-20260728')='902b6173f5ee366a03a79f282777a67579ab8262598bdab89e588533cfd19ff1'
    AND (SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\n'),256) FROM forwarder_quote_transport_fee WHERE quote_version_id=(SELECT id FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728') AND service_code='YT-SAU-SEA-FBN-RUH-20260728')='74ff49fbd0863e298bbb9244a8db8c2429e12ce84d9dbf7dd7ac2a8df9e832f8'
    AND (SELECT COUNT(*) FROM forwarder_quote_base_price price
      JOIN forwarder_quote_version version ON version.id=price.quote_version_id
      LEFT JOIN forwarder_quote_numeric_adjustment adjustment
        ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id
       AND adjustment.field_name='unit_price' AND adjustment.adjustment_status='ACTIVE'
      WHERE version.version_no='YT-SAU-20260728'
        AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023')
        AND adjustment.id IS NULL
        AND COALESCE(adjustment.adjusted_value,price.unit_price)=
          CASE RIGHT(price.cargo_category_code,3) WHEN '020' THEN 1540
            WHEN '021' THEN 1900 WHEN '022' THEN 2040 WHEN '023' THEN 2290 END)=4
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND table_type='BASE TABLE' AND UPPER(engine)='INNODB' AND table_collation='utf8mb4_unicode_ci' AND UPPER(row_format)='DYNAMIC')=1
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable) ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility')='id:bigint:NO,owner_user_id:bigint:NO,product_master_id:bigint:YES,product_variant_id:bigint:YES,logical_store_id:bigint:NO,source_store_code:varchar(100):YES,partner_sku:varchar(100):NO,site_code:varchar(20):NO,forwarder_code:varchar(80):NO,transport_mode:varchar(20):NO,eligibility_status:varchar(40):NO,effective_from:date:NO,effective_to:date:YES,version:int:NO,is_deleted:bit(1):NO,active_scope_slot:varchar(512):YES,created_by:bigint:YES,updated_by:bigint:YES,gmt_create:datetime:NO,gmt_updated:datetime:NO'
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',collation_name) ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND data_type='varchar')='source_store_code:utf8mb4_unicode_ci,partner_sku:utf8mb4_bin,site_code:utf8mb4_bin,forwarder_code:utf8mb4_bin,transport_mode:utf8mb4_bin,eligibility_status:utf8mb4_unicode_ci,active_scope_slot:utf8mb4_bin'
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_default IS NULL)=16
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND extra='')=17
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND generation_expression='')=19
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='version' AND column_default='1' AND extra='')=1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='is_deleted' AND column_default='b''0''' AND extra='')=1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='gmt_create' AND UPPER(column_default)='CURRENT_TIMESTAMP' AND UPPER(extra)='DEFAULT_GENERATED')=1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='gmt_updated' AND UPPER(column_default)='CURRENT_TIMESTAMP' AND UPPER(extra)='DEFAULT_GENERATED ON UPDATE CURRENT_TIMESTAMP')=1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND column_name='active_scope_slot' AND UPPER(extra)='STORED GENERATED' AND REPLACE(REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(generation_expression),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),CONCAT(CHAR(92),'0'),'0'),'_utf8mb4',''),'((_binary|b)?''0''|0x00|0b0)','0'),'[()[:space:]]+',''),'charsetutf8mb4',''),'charactersetutf8mb4','')='casewhenis_deleted=0andeffective_toisnullthenconcatlengthcastowner_user_idaschar,''#'',castowner_user_idaschar,lengthcastlogical_store_idaschar,''#'',castlogical_store_idaschar,lengthuppertrimpartner_sku,''#'',uppertrimpartner_sku,lengthuppertrimsite_code,''#'',uppertrimsite_code,lengthuppertrimforwarder_code,''#'',uppertrimforwarder_code,lengthuppertrimtransport_mode,''#'',uppertrimtransport_modeelsenullend')=1
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name) ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility')='idx_pfte_effective:1:1:effective_from,idx_pfte_effective:1:2:effective_to,idx_pfte_scope_history:1:1:owner_user_id,idx_pfte_scope_history:1:2:logical_store_id,idx_pfte_scope_history:1:3:partner_sku,idx_pfte_scope_history:1:4:site_code,idx_pfte_scope_history:1:5:forwarder_code,idx_pfte_scope_history:1:6:transport_mode,idx_pfte_scope_history:1:7:version,idx_pfte_scope_history:1:8:id,PRIMARY:0:1:id,uk_pfte_active_scope:0:1:active_scope_slot'
    AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND index_type='BTREE' AND is_visible='YES' AND sub_part IS NULL AND expression IS NULL AND collation='A')=12
    AND (SELECT GROUP_CONCAT(CONCAT(constraint_name,':',enforced) ORDER BY constraint_name SEPARATOR ',') FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' AND constraint_type='CHECK')='chk_pfte_effective:YES,chk_pfte_product_scope:YES,chk_pfte_scope_codes:YES,chk_pfte_status:YES,chk_pfte_version:YES'
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility')=7
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_status' AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')='casteligibility_statusasbinaryincast''inquiry_required''asbinary,cast''unsupported''asbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_version' AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')='version>0')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_effective' AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')='effective_toisnulloreffective_to>=effective_from')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_product_scope' AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')='owner_user_id>0andlogical_store_id>0andlengthtrimpartner_sku>0andcastpartner_skuasbinary=castuppertrimpartner_skuasbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_scope_codes' AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')='castsite_codeasbinary=castuppertrimsite_codeasbinaryandlengthtrimsite_code>0andcastforwarder_codeasbinary=castuppertrimforwarder_codeasbinaryandlengthtrimforwarder_code>0andcasttransport_modeasbinary=castuppertrimtransport_modeasbinaryandcasttransport_modeasbinaryincast''air''asbinary,cast''sea''asbinary')=1
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_transport_eligibility WHERE owner_user_id<=0 OR logical_store_id<=0 OR OCTET_LENGTH(TRIM(partner_sku))=0 OR CAST(partner_sku AS BINARY)<>CAST(UPPER(TRIM(partner_sku)) AS BINARY) OR CAST(eligibility_status AS BINARY) NOT IN (CAST('INQUIRY_REQUIRED' AS BINARY),CAST('UNSUPPORTED' AS BINARY)) OR CAST(site_code AS BINARY)<>CAST(UPPER(TRIM(site_code)) AS BINARY) OR OCTET_LENGTH(TRIM(site_code))=0 OR CAST(forwarder_code AS BINARY)<>CAST(UPPER(TRIM(forwarder_code)) AS BINARY) OR OCTET_LENGTH(TRIM(forwarder_code))=0 OR CAST(transport_mode AS BINARY)<>CAST(UPPER(TRIM(transport_mode)) AS BINARY) OR CAST(transport_mode AS BINARY) NOT IN (CAST('AIR' AS BINARY),CAST('SEA' AS BINARY)) OR version<=0 OR effective_from IS NULL OR (effective_to IS NOT NULL AND effective_to<effective_from))
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_transport_eligibility WHERE (is_deleted=b'0' AND effective_to IS NULL AND (active_scope_slot IS NULL OR CAST(active_scope_slot AS BINARY)<>CAST(CONCAT(OCTET_LENGTH(CAST(owner_user_id AS CHAR)),'#',CAST(owner_user_id AS CHAR),OCTET_LENGTH(CAST(logical_store_id AS CHAR)),'#',CAST(logical_store_id AS CHAR),OCTET_LENGTH(UPPER(TRIM(partner_sku))),'#',UPPER(TRIM(partner_sku)),OCTET_LENGTH(UPPER(TRIM(site_code))),'#',UPPER(TRIM(site_code)),OCTET_LENGTH(UPPER(TRIM(forwarder_code))),'#',UPPER(TRIM(forwarder_code)),OCTET_LENGTH(UPPER(TRIM(transport_mode))),'#',UPPER(TRIM(transport_mode))) AS BINARY))) OR ((is_deleted<>b'0' OR effective_to IS NOT NULL) AND active_scope_slot IS NOT NULL))
    AND NOT EXISTS (SELECT active_scope_slot FROM product_forwarder_transport_eligibility WHERE active_scope_slot IS NOT NULL GROUP BY active_scope_slot HAVING COUNT(*)>1)
    AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table='product_forwarder_transport_eligibility')=0
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor' AND table_type='BASE TABLE' AND UPPER(engine)='INNODB' AND table_collation='utf8mb4_unicode_ci' AND UPPER(row_format)='DYNAMIC')=1
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable) ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor')='owner_user_id:bigint:NO,logical_store_id:bigint:NO,partner_sku_normalized:varchar(100):NO,gmt_create:datetime:NO,gmt_updated:datetime:NO'
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor' AND column_default IS NULL AND extra='' AND generation_expression='')=5
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor' AND column_name='partner_sku_normalized' AND character_set_name='utf8mb4' AND collation_name='utf8mb4_bin')=1
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name) ORDER BY index_name,seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor')='PRIMARY:0:1:owner_user_id,PRIMARY:0:2:logical_store_id,PRIMARY:0:3:partner_sku_normalized'
    AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor' AND index_type='BTREE' AND is_visible='YES' AND sub_part IS NULL AND expression IS NULL AND collation='A')=3
    AND (SELECT GROUP_CONCAT(CONCAT(constraint_name,':',enforced) ORDER BY constraint_name SEPARATOR ',') FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor' AND constraint_type='CHECK')='chk_pfea_normalized_scope:YES'
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_eligibility_scope_anchor')=2
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfea_normalized_scope' AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')='owner_user_id>0andlogical_store_id>0andlengthtrimpartner_sku_normalized>0andcastpartner_sku_normalizedasbinary=castuppertrimpartner_sku_normalizedasbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table='product_forwarder_eligibility_scope_anchor')=0
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_eligibility_scope_anchor WHERE owner_user_id<=0 OR logical_store_id<=0 OR OCTET_LENGTH(TRIM(partner_sku_normalized))=0 OR CAST(partner_sku_normalized AS BINARY)<>CAST(UPPER(TRIM(partner_sku_normalized)) AS BINARY))
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_transport_eligibility eligibility LEFT JOIN product_forwarder_eligibility_scope_anchor anchor ON anchor.owner_user_id=eligibility.owner_user_id AND anchor.logical_store_id=eligibility.logical_store_id AND anchor.partner_sku_normalized=eligibility.partner_sku WHERE anchor.owner_user_id IS NULL)
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='procurement_shipping_order_line'
        AND column_name='eligibility_status_snapshot' AND data_type='varchar'
        AND character_maximum_length=40 AND is_nullable='YES' AND column_default IS NULL
        AND extra='' AND generation_expression='')=1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='procurement_shipping_order_line' AND constraint_name='chk_shipping_line_eligibility_snapshot' AND constraint_type='CHECK' AND enforced='YES')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_shipping_line_eligibility_snapshot' AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')='eligibility_status_snapshotisnullorcasteligibility_status_snapshotasbinaryincast''supported''asbinary,cast''inquiry_required''asbinary,cast''unsupported''asbinary')=1
    AND NOT EXISTS (SELECT 1 FROM procurement_shipping_order_line
      WHERE eligibility_status_snapshot IS NOT NULL
        AND CAST(eligibility_status_snapshot AS BINARY) NOT IN (CAST('SUPPORTED' AS BINARY),CAST('INQUIRY_REQUIRED' AS BINARY),CAST('UNSUPPORTED' AS BINARY)))
    AND (SELECT COUNT(*) FROM product_management_id_sequence WHERE sequence_name='product_forwarder_transport_eligibility' AND next_id>=GREATEST(COALESCE((SELECT MAX(id) FROM product_forwarder_transport_eligibility),370000),370000))=1,
    1,
    0
);
