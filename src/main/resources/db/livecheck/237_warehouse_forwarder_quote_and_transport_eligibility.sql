SELECT IF(
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
        AND table_name IN ('forwarder_quote_numeric_adjustment',
          'forwarder_quote_numeric_adjustment_log'))=2
    AND (SELECT COUNT(*) FROM information_schema.triggers
      WHERE trigger_schema=DATABASE()
        AND event_object_table IN ('forwarder_quote_numeric_adjustment',
          'forwarder_quote_numeric_adjustment_log'))=6
    AND (SELECT COUNT(*) FROM information_schema.triggers
      WHERE trigger_schema=DATABASE() AND action_timing='BEFORE'
        AND action_orientation='ROW' AND action_order=1
        AND LOWER(REGEXP_REPLACE(action_statement,'[[:space:]]+',' '))
          REGEXP '^signal sqlstate( value)? ''45000'' set message_text[[:space:]]*=[[:space:]]*''legacy numeric adjustment writer fenced by migration 237''$'
        AND ((trigger_name='trg_fq_numeric_adjustment_retired_bi'
              AND event_object_table='forwarder_quote_numeric_adjustment'
              AND event_manipulation='INSERT')
          OR (trigger_name='trg_fq_numeric_adjustment_retired_bu'
              AND event_object_table='forwarder_quote_numeric_adjustment'
              AND event_manipulation='UPDATE')
          OR (trigger_name='trg_fq_numeric_adjustment_retired_bd'
              AND event_object_table='forwarder_quote_numeric_adjustment'
              AND event_manipulation='DELETE')
          OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bi'
              AND event_object_table='forwarder_quote_numeric_adjustment_log'
              AND event_manipulation='INSERT')
          OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bu'
              AND event_object_table='forwarder_quote_numeric_adjustment_log'
              AND event_manipulation='UPDATE')
          OR (trigger_name='trg_fq_numeric_adjustment_log_retired_bd'
              AND event_object_table='forwarder_quote_numeric_adjustment_log'
              AND event_manipulation='DELETE')))=6
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='forwarder_quote_numeric_adjustment')
      ='id:bigint,quote_version_id:bigint,target_type:varchar(60),target_id:bigint,field_name:varchar(80),original_value:decimal(12,4),adjusted_value:decimal(12,4),currency:varchar(20),reason:varchar(500),adjustment_status:varchar(40),created_by:bigint,updated_by:bigint,gmt_create:datetime,gmt_updated:datetime'
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='forwarder_quote_numeric_adjustment_log')
      ='id:bigint,adjustment_id:bigint,quote_version_id:bigint,target_type:varchar(60),target_id:bigint,field_name:varchar(80),before_value:decimal(12,4),after_value:decimal(12,4),action_type:varchar(60),reason:varchar(500),operated_by:bigint,gmt_create:datetime'
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='forwarder_quote_numeric_adjustment')
      ='idx_fq_numeric_adjustment_version:1:1:quote_version_id,PRIMARY:0:1:id,uk_fq_numeric_adjustment_current:0:1:target_type,uk_fq_numeric_adjustment_current:0:2:target_id,uk_fq_numeric_adjustment_current:0:3:field_name,uk_fq_numeric_adjustment_current:0:4:adjustment_status'
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='forwarder_quote_numeric_adjustment_log')
      ='idx_fq_numeric_adjustment_log_target:1:1:target_type,idx_fq_numeric_adjustment_log_target:1:2:target_id,idx_fq_numeric_adjustment_log_target:1:3:field_name,idx_fq_numeric_adjustment_log_version:1:1:quote_version_id,PRIMARY:0:1:id'
    AND (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND table_type='BASE TABLE' AND UPPER(engine)='INNODB'
        AND table_collation='utf8mb4_unicode_ci'
        AND UPPER(row_format)='DYNAMIC')=1
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility')
      ='id:bigint:NO,owner_user_id:bigint:NO,product_master_id:bigint:YES,product_variant_id:bigint:YES,logical_store_id:bigint:NO,source_store_code:varchar(100):YES,partner_sku:varchar(100):NO,site_code:varchar(20):NO,forwarder_code:varchar(80):NO,transport_mode:varchar(20):NO,eligibility_status:varchar(40):NO,effective_from:date:NO,effective_to:date:YES,version:int:NO,is_deleted:bit(1):NO,active_scope_slot:varchar(512):YES,created_by:bigint:YES,updated_by:bigint:YES,gmt_create:datetime:NO,gmt_updated:datetime:NO'
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',collation_name)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND data_type='varchar')
      ='source_store_code:utf8mb4_unicode_ci,partner_sku:utf8mb4_bin,site_code:utf8mb4_bin,forwarder_code:utf8mb4_bin,transport_mode:utf8mb4_bin,eligibility_status:utf8mb4_unicode_ci,active_scope_slot:utf8mb4_bin'
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_default IS NULL)=16
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND extra='')=17
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND generation_expression='')=19
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_name='version' AND column_default='1' AND extra='')=1
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_name='is_deleted' AND column_default='b''0'''
        AND extra='')=1
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_name='gmt_create'
        AND UPPER(column_default)='CURRENT_TIMESTAMP'
        AND UPPER(extra)='DEFAULT_GENERATED')=1
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_name='gmt_updated'
        AND UPPER(column_default)='CURRENT_TIMESTAMP'
        AND UPPER(extra)='DEFAULT_GENERATED ON UPDATE CURRENT_TIMESTAMP')=1
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND column_name='active_scope_slot' AND UPPER(extra)='STORED GENERATED'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(generation_expression),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),CONCAT(CHAR(92),'0'),'0'),'_utf8mb4',''),'((_binary|b)?''0''|0x00|0b0)','0'),'[()[:space:]]+',''),'charsetutf8mb4',''),'charactersetutf8mb4','')
          ='casewhenis_deleted=0andeffective_toisnullthenconcatlengthcastowner_user_idaschar,''#'',castowner_user_idaschar,lengthcastlogical_store_idaschar,''#'',castlogical_store_idaschar,lengthuppertrimpartner_sku,''#'',uppertrimpartner_sku,lengthuppertrimsite_code,''#'',uppertrimsite_code,lengthuppertrimforwarder_code,''#'',uppertrimforwarder_code,lengthuppertrimtransport_mode,''#'',uppertrimtransport_modeelsenullend')=1
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility')
      ='idx_pfte_effective:1:1:effective_from,idx_pfte_effective:1:2:effective_to,idx_pfte_scope_history:1:1:owner_user_id,idx_pfte_scope_history:1:2:logical_store_id,idx_pfte_scope_history:1:3:partner_sku,idx_pfte_scope_history:1:4:site_code,idx_pfte_scope_history:1:5:forwarder_code,idx_pfte_scope_history:1:6:transport_mode,idx_pfte_scope_history:1:7:version,idx_pfte_scope_history:1:8:id,PRIMARY:0:1:id,uk_pfte_active_scope:0:1:active_scope_slot'
    AND (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND index_type='BTREE' AND is_visible='YES' AND sub_part IS NULL
        AND expression IS NULL AND collation='A')=12
    AND (SELECT GROUP_CONCAT(CONCAT(constraint_name,':',enforced)
      ORDER BY constraint_name SEPARATOR ',')
      FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility'
        AND constraint_type='CHECK')
      ='chk_pfte_effective:YES,chk_pfte_product_scope:YES,chk_pfte_scope_codes:YES,chk_pfte_status:YES,chk_pfte_version:YES'
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema=DATABASE()
        AND table_name='product_forwarder_transport_eligibility')=7
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_status'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')
          ='casteligibility_statusasbinaryincast''inquiry_required''asbinary,cast''unsupported''asbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_version'
        AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')='version>0')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_effective'
        AND REGEXP_REPLACE(LOWER(check_clause),'[`()[:space:]]+','')
          ='effective_toisnulloreffective_to>=effective_from')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE()
        AND constraint_name='chk_pfte_product_scope'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')
          ='owner_user_id>0andlogical_store_id>0andlengthtrimpartner_sku>0andcastpartner_skuasbinary=castuppertrimpartner_skuasbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE() AND constraint_name='chk_pfte_scope_codes'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')
          ='castsite_codeasbinary=castuppertrimsite_codeasbinaryandlengthtrimsite_code>0andcastforwarder_codeasbinary=castuppertrimforwarder_codeasbinaryandlengthtrimforwarder_code>0andcasttransport_modeasbinary=castuppertrimtransport_modeasbinaryandcasttransport_modeasbinaryincast''air''asbinary,cast''sea''asbinary')=1
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_transport_eligibility
      WHERE owner_user_id<=0 OR logical_store_id<=0
        OR OCTET_LENGTH(TRIM(partner_sku))=0
        OR CAST(partner_sku AS BINARY)<>CAST(UPPER(TRIM(partner_sku)) AS BINARY)
        OR CAST(eligibility_status AS BINARY) NOT IN
          (CAST('INQUIRY_REQUIRED' AS BINARY),CAST('UNSUPPORTED' AS BINARY))
        OR CAST(site_code AS BINARY)<>CAST(UPPER(TRIM(site_code)) AS BINARY)
        OR OCTET_LENGTH(TRIM(site_code))=0
        OR CAST(forwarder_code AS BINARY)<>CAST(UPPER(TRIM(forwarder_code)) AS BINARY)
        OR OCTET_LENGTH(TRIM(forwarder_code))=0
        OR CAST(transport_mode AS BINARY)<>CAST(UPPER(TRIM(transport_mode)) AS BINARY)
        OR CAST(transport_mode AS BINARY) NOT IN
          (CAST('AIR' AS BINARY),CAST('SEA' AS BINARY))
        OR version<=0 OR effective_from IS NULL
        OR (effective_to IS NOT NULL AND effective_to<effective_from))
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_transport_eligibility
      WHERE (is_deleted=b'0' AND effective_to IS NULL
        AND (active_scope_slot IS NULL
          OR CAST(active_scope_slot AS BINARY)<>CAST(CONCAT(
            OCTET_LENGTH(CAST(owner_user_id AS CHAR)),'#',CAST(owner_user_id AS CHAR),
            OCTET_LENGTH(CAST(logical_store_id AS CHAR)),'#',CAST(logical_store_id AS CHAR),
            OCTET_LENGTH(UPPER(TRIM(partner_sku))),'#',UPPER(TRIM(partner_sku)),
            OCTET_LENGTH(UPPER(TRIM(site_code))),'#',UPPER(TRIM(site_code)),
            OCTET_LENGTH(UPPER(TRIM(forwarder_code))),'#',UPPER(TRIM(forwarder_code)),
            OCTET_LENGTH(UPPER(TRIM(transport_mode))),'#',UPPER(TRIM(transport_mode))) AS BINARY)))
        OR ((is_deleted<>b'0' OR effective_to IS NOT NULL)
          AND active_scope_slot IS NOT NULL))
    AND NOT EXISTS (SELECT active_scope_slot
      FROM product_forwarder_transport_eligibility
      WHERE active_scope_slot IS NOT NULL GROUP BY active_scope_slot
      HAVING COUNT(*)>1)
    AND (SELECT COUNT(*) FROM information_schema.triggers
      WHERE trigger_schema=DATABASE()
        AND event_object_table='product_forwarder_transport_eligibility')=0
    AND (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor'
        AND table_type='BASE TABLE' AND UPPER(engine)='INNODB'
        AND table_collation='utf8mb4_unicode_ci'
        AND UPPER(row_format)='DYNAMIC')=1
    AND (SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable)
      ORDER BY ordinal_position SEPARATOR ',') FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor')
      ='owner_user_id:bigint:NO,logical_store_id:bigint:NO,partner_sku_normalized:varchar(100):NO,gmt_create:datetime:NO,gmt_updated:datetime:NO'
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor'
        AND column_default IS NULL AND extra='' AND generation_expression='')=5
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor'
        AND column_name='partner_sku_normalized'
        AND character_set_name='utf8mb4' AND collation_name='utf8mb4_bin')=1
    AND (SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
      ORDER BY index_name,seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor')
      ='PRIMARY:0:1:owner_user_id,PRIMARY:0:2:logical_store_id,PRIMARY:0:3:partner_sku_normalized'
    AND (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor'
        AND index_type='BTREE' AND is_visible='YES' AND sub_part IS NULL
        AND expression IS NULL AND collation='A')=3
    AND (SELECT GROUP_CONCAT(CONCAT(constraint_name,':',enforced)
      ORDER BY constraint_name SEPARATOR ',')
      FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor'
        AND constraint_type='CHECK')='chk_pfea_normalized_scope:YES'
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema=DATABASE()
        AND table_name='product_forwarder_eligibility_scope_anchor')=2
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE()
        AND constraint_name='chk_pfea_normalized_scope'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')
          ='owner_user_id>0andlogical_store_id>0andlengthtrimpartner_sku_normalized>0andcastpartner_sku_normalizedasbinary=castuppertrimpartner_sku_normalizedasbinary')=1
    AND (SELECT COUNT(*) FROM information_schema.triggers
      WHERE trigger_schema=DATABASE()
        AND event_object_table='product_forwarder_eligibility_scope_anchor')=0
    AND NOT EXISTS (SELECT 1 FROM product_forwarder_eligibility_scope_anchor
      WHERE owner_user_id<=0 OR logical_store_id<=0
        OR OCTET_LENGTH(TRIM(partner_sku_normalized))=0
        OR CAST(partner_sku_normalized AS BINARY)
          <>CAST(UPPER(TRIM(partner_sku_normalized)) AS BINARY))
    AND NOT EXISTS (SELECT 1
      FROM product_forwarder_transport_eligibility eligibility
      LEFT JOIN product_forwarder_eligibility_scope_anchor anchor
        ON anchor.owner_user_id=eligibility.owner_user_id
       AND anchor.logical_store_id=eligibility.logical_store_id
       AND anchor.partner_sku_normalized=eligibility.partner_sku
      WHERE anchor.owner_user_id IS NULL)
    AND (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE()
        AND table_name='procurement_shipping_order_line'
        AND column_name='eligibility_status_snapshot' AND data_type='varchar'
        AND character_maximum_length=40 AND is_nullable='YES'
        AND column_default IS NULL AND extra='' AND generation_expression='')=1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema=DATABASE()
        AND table_name='procurement_shipping_order_line'
        AND constraint_name='chk_shipping_line_eligibility_snapshot'
        AND constraint_type='CHECK' AND enforced='YES')=1
    AND (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema=DATABASE()
        AND constraint_name='chk_shipping_line_eligibility_snapshot'
        AND REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),'octet_length','length')
          ='eligibility_status_snapshotisnullorcasteligibility_status_snapshotasbinaryincast''supported''asbinary,cast''inquiry_required''asbinary,cast''unsupported''asbinary')=1
    AND NOT EXISTS (SELECT 1 FROM procurement_shipping_order_line
      WHERE eligibility_status_snapshot IS NOT NULL
        AND CAST(eligibility_status_snapshot AS BINARY) NOT IN
          (CAST('SUPPORTED' AS BINARY),CAST('INQUIRY_REQUIRED' AS BINARY),
           CAST('UNSUPPORTED' AS BINARY)))
    AND (SELECT COUNT(*) FROM product_management_id_sequence
      WHERE sequence_name='product_forwarder_transport_eligibility'
        AND next_id>=GREATEST(COALESCE(
          (SELECT MAX(id) FROM product_forwarder_transport_eligibility),370000),
          370000))=1,
    1,
    0
);
