package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductAttributeDictionaryFieldRecord;
import com.nuono.next.product.ProductAttributeDictionaryOptionRecord;
import com.nuono.next.product.ProductAttributeTemplateRecord;
import com.nuono.next.product.ProductAttributeTemplateRefreshCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductAttributeTemplateMapper {

    @Select({
            "SELECT",
            "  id, project_code, store_code, product_fulltype, source, status, template_hash,",
            "  raw_json, normalized_json, fetched_at, error_message",
            "FROM noon_attribute_template",
            "WHERE project_code = #{projectCode}",
            "  AND store_code = #{storeCode}",
            "  AND product_fulltype = #{productFulltype}",
            "LIMIT 1"
    })
    ProductAttributeTemplateRecord selectByScope(
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype
    );

    @Insert({
            "INSERT INTO noon_attribute_template (",
            "  id, project_code, store_code, product_fulltype, source, status, template_hash,",
            "  raw_json, normalized_json, fetched_at, error_message, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{projectCode}, #{storeCode}, #{productFulltype}, #{source}, #{status}, #{templateHash},",
            "  #{rawJson}, #{normalizedJson}, #{fetchedAt}, #{errorMessage}, #{operatorUserId}, #{operatorUserId},",
            "  NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  source = VALUES(source),",
            "  status = VALUES(status),",
            "  template_hash = VALUES(template_hash),",
            "  raw_json = VALUES(raw_json),",
            "  normalized_json = VALUES(normalized_json),",
            "  fetched_at = VALUES(fetched_at),",
            "  error_message = VALUES(error_message),",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsert(
            @Param("id") Long id,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype,
            @Param("source") String source,
            @Param("status") String status,
            @Param("templateHash") String templateHash,
            @Param("rawJson") String rawJson,
            @Param("normalizedJson") String normalizedJson,
            @Param("fetchedAt") java.time.LocalDateTime fetchedAt,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "  id, project_code, store_code, product_fulltype, attribute_code,",
            "  label_en, label_ar, label_zh, group_name, input_kind, `required`, `grouping`, visible_seller,",
            "  dictionary_source, sort_order, fetched_at",
            "FROM noon_attribute_field",
            "WHERE is_deleted = 0",
            "  AND (",
            "    (project_code = #{projectCode} AND store_code = #{storeCode} AND product_fulltype = #{productFulltype})",
            "    OR (project_code = '*' AND store_code = '*' AND product_fulltype = '*')",
            "  )",
            "ORDER BY",
            "  CASE WHEN project_code = '*' THEN 1 ELSE 0 END,",
            "  sort_order ASC, id ASC"
    })
    List<ProductAttributeDictionaryFieldRecord> selectDictionaryFields(
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype
    );

    @Select({
            "SELECT",
            "  id, project_code, store_code, product_fulltype, attribute_code,",
            "  label_en, label_ar, label_zh, group_name, input_kind, `required`, `grouping`, visible_seller,",
            "  dictionary_source, sort_order, fetched_at",
            "FROM noon_attribute_field",
            "WHERE project_code = #{projectCode}",
            "  AND store_code = #{storeCode}",
            "  AND product_fulltype = #{productFulltype}",
            "  AND attribute_code = #{attributeCode}",
            "LIMIT 1"
    })
    ProductAttributeDictionaryFieldRecord selectDictionaryFieldByScopeCode(
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype,
            @Param("attributeCode") String attributeCode
    );

    @Select({
            "<script>",
            "SELECT field_id, option_value, label_en, label_ar, label_zh, sort_order",
            "FROM noon_attribute_option",
            "WHERE is_deleted = 0",
            "  AND field_id IN",
            "  <foreach item='fieldId' collection='fieldIds' open='(' separator=',' close=')'>",
            "    #{fieldId}",
            "  </foreach>",
            "ORDER BY field_id ASC, sort_order ASC, id ASC",
            "</script>"
    })
    List<ProductAttributeDictionaryOptionRecord> selectDictionaryOptions(@Param("fieldIds") List<Long> fieldIds);

    @Select({
            "<script>",
            "SELECT field_id, unit_value AS option_value, label_en, label_ar, label_zh, sort_order",
            "FROM noon_attribute_unit_option",
            "WHERE is_deleted = 0",
            "  AND field_id IN",
            "  <foreach item='fieldId' collection='fieldIds' open='(' separator=',' close=')'>",
            "    #{fieldId}",
            "  </foreach>",
            "ORDER BY field_id ASC, sort_order ASC, id ASC",
            "</script>"
    })
    List<ProductAttributeDictionaryOptionRecord> selectDictionaryUnitOptions(@Param("fieldIds") List<Long> fieldIds);

    @Update({
            "UPDATE noon_attribute_field",
            "SET is_deleted = b'1', gmt_updated = NOW()",
            "WHERE project_code = #{projectCode}",
            "  AND store_code = #{storeCode}",
            "  AND product_fulltype = #{productFulltype}",
            "  AND dictionary_source = 'official-template'"
    })
    int markOfficialFieldsDeleted(
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype
    );

    @Insert({
            "INSERT INTO noon_attribute_field (",
            "  id, project_code, store_code, product_fulltype, attribute_code,",
            "  label_en, label_ar, group_name, input_kind, `required`, `grouping`, visible_seller,",
            "  dictionary_source, sort_order, fetched_at, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{projectCode}, #{storeCode}, #{productFulltype}, #{attributeCode},",
            "  #{labelEn}, #{labelAr}, #{groupName}, #{inputKind}, #{required}, #{grouping}, #{visibleSeller},",
            "  #{dictionarySource}, #{sortOrder}, #{fetchedAt}, b'0', #{operatorUserId}, #{operatorUserId},",
            "  NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  label_en = VALUES(label_en),",
            "  label_ar = VALUES(label_ar),",
            "  group_name = VALUES(group_name),",
            "  input_kind = VALUES(input_kind),",
            "  `required` = VALUES(`required`),",
            "  `grouping` = VALUES(`grouping`),",
            "  visible_seller = VALUES(visible_seller),",
            "  dictionary_source = VALUES(dictionary_source),",
            "  sort_order = VALUES(sort_order),",
            "  fetched_at = VALUES(fetched_at),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertDictionaryField(
            @Param("id") Long id,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype,
            @Param("attributeCode") String attributeCode,
            @Param("labelEn") String labelEn,
            @Param("labelAr") String labelAr,
            @Param("groupName") String groupName,
            @Param("inputKind") String inputKind,
            @Param("required") Boolean required,
            @Param("grouping") Boolean grouping,
            @Param("visibleSeller") Boolean visibleSeller,
            @Param("dictionarySource") String dictionarySource,
            @Param("sortOrder") Integer sortOrder,
            @Param("fetchedAt") LocalDateTime fetchedAt,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE noon_attribute_option",
            "SET is_deleted = b'1', gmt_updated = NOW()",
            "WHERE field_id = #{fieldId}"
    })
    int markOptionsDeleted(@Param("fieldId") Long fieldId);

    @Update({
            "UPDATE noon_attribute_unit_option",
            "SET is_deleted = b'1', gmt_updated = NOW()",
            "WHERE field_id = #{fieldId}"
    })
    int markUnitOptionsDeleted(@Param("fieldId") Long fieldId);

    @Insert({
            "INSERT INTO noon_attribute_option (",
            "  id, field_id, option_value, label_en, label_ar, sort_order,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fieldId}, #{optionValue}, #{labelEn}, #{labelAr}, #{sortOrder},",
            "  b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  label_en = VALUES(label_en),",
            "  label_ar = VALUES(label_ar),",
            "  sort_order = VALUES(sort_order),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertDictionaryOption(
            @Param("id") Long id,
            @Param("fieldId") Long fieldId,
            @Param("optionValue") String optionValue,
            @Param("labelEn") String labelEn,
            @Param("labelAr") String labelAr,
            @Param("sortOrder") Integer sortOrder,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO noon_attribute_unit_option (",
            "  id, field_id, unit_value, label_en, label_ar, sort_order,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fieldId}, #{unitValue}, #{labelEn}, #{labelAr}, #{sortOrder},",
            "  b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  label_en = VALUES(label_en),",
            "  label_ar = VALUES(label_ar),",
            "  sort_order = VALUES(sort_order),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertDictionaryUnitOption(
            @Param("id") Long id,
            @Param("fieldId") Long fieldId,
            @Param("unitValue") String unitValue,
            @Param("labelEn") String labelEn,
            @Param("labelAr") String labelAr,
            @Param("sortOrder") Integer sortOrder,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "  ranked.ownerUserId, ranked.projectCode, ranked.storeCode, ranked.productFulltype,",
            "  ranked.authProjectCode, ranked.authStoreCode",
            "FROM (",
            "  SELECT",
            "    auth.owner_user_id AS ownerUserId,",
            "    '*' AS projectCode,",
            "    '*' AS storeCode,",
            "    t.product_fulltype AS productFulltype,",
            "    auth.project_code AS authProjectCode,",
            "    auth.store_code AS authStoreCode,",
            "    t.fetched_at AS fetchedAt,",
            "    ROW_NUMBER() OVER (",
            "      PARTITION BY t.product_fulltype",
            "      ORDER BY",
            "        CASE WHEN t.project_code = '*' AND t.store_code = '*' THEN 0 ELSE 1 END,",
            "        t.fetched_at ASC, auth.owner_user_id ASC, auth.store_code ASC",
            "    ) AS templateRank",
            "  FROM noon_attribute_template t",
            "  JOIN (",
            "    SELECT ls.owner_user_id, ls.project_code, lss.store_code",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "    WHERE lss.is_deleted = b'0'",
            "  ) auth ON (t.project_code = '*' OR auth.project_code = t.project_code)",
            "    AND (t.store_code = '*' OR auth.store_code = t.store_code)",
            "  WHERE t.status = 'ready'",
            "    AND t.fetched_at < DATE_SUB(NOW(), INTERVAL #{staleDays} DAY)",
            ") ranked",
            "WHERE ranked.templateRank = 1",
            "ORDER BY ranked.fetchedAt ASC",
            "LIMIT #{limit}"
    })
    List<ProductAttributeTemplateRefreshCandidate> selectRefreshCandidates(
            @Param("staleDays") Integer staleDays,
            @Param("limit") Integer limit
    );

    @Insert({
            "INSERT INTO noon_attribute_template_sync_log (",
            "  id, project_code, store_code, product_fulltype, sync_type, status,",
            "  error_message, started_at, finished_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{projectCode}, #{storeCode}, #{productFulltype}, #{syncType}, #{status},",
            "  #{errorMessage}, #{startedAt}, #{finishedAt}, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSyncLog(
            @Param("id") Long id,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype,
            @Param("syncType") String syncType,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("operatorUserId") Long operatorUserId
    );
}
