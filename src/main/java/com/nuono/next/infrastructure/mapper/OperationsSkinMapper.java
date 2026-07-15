package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsskin.OperationsSkinAssetRecord;
import com.nuono.next.operationsskin.OperationsSkinComponentRecord;
import com.nuono.next.operationsskin.OperationsSkinRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OperationsSkinMapper {

    @Insert({
            "INSERT INTO operations_image_skin (",
            "  owner_user_id, store_code, skin_name, status, cover_image_url, style_description, remark,",
            "  sort_order, created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{ownerUserId}, #{storeCode}, #{skinName}, #{status}, #{coverImageUrl}, #{styleDescription}, #{remark},",
            "  #{sortOrder}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    Long insertSkin(OperationsSkinRecord record);

    @Update({
            "UPDATE operations_image_skin",
            "SET",
            "  skin_name = #{skinName},",
            "  status = #{status},",
            "  cover_image_url = #{coverImageUrl},",
            "  style_description = #{styleDescription},",
            "  remark = #{remark},",
            "  sort_order = #{sortOrder},",
            "  updated_by = #{updatedBy},",
            "  updated_at = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND deleted = b'0'"
    })
    int updateSkin(OperationsSkinRecord record);

    @Update({
            "UPDATE operations_image_skin",
            "SET",
            "  status = #{status},",
            "  updated_by = #{updatedBy},",
            "  updated_at = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND deleted = b'0'"
    })
    int updateStatus(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE operations_image_skin",
            "SET",
            "  deleted = b'1',",
            "  updated_by = #{updatedBy},",
            "  updated_at = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND deleted = b'0'"
    })
    int softDeleteSkin(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("updatedBy") Long updatedBy
    );

    @Results(id = "operationsSkinRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "owner_user_id", property = "ownerUserId"),
            @Result(column = "store_code", property = "storeCode"),
            @Result(column = "skin_name", property = "skinName"),
            @Result(column = "status", property = "status"),
            @Result(column = "cover_image_url", property = "coverImageUrl"),
            @Result(column = "style_description", property = "styleDescription"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class),
            @Result(column = "asset_count", property = "assetCount"),
            @Result(column = "hero_component_count", property = "heroComponentCount")
    })
    @Select({
            "<script>",
            "SELECT",
            "  s.id,",
            "  s.owner_user_id,",
            "  s.store_code,",
            "  s.skin_name,",
            "  s.status,",
            "  s.cover_image_url,",
            "  s.style_description,",
            "  s.remark,",
            "  s.sort_order,",
            "  s.created_by,",
            "  s.updated_by,",
            "  s.created_at,",
            "  s.updated_at,",
            "  s.deleted,",
            "  COALESCE(asset_counts.asset_count, 0) AS asset_count,",
            "  COALESCE(component_counts.hero_component_count, 0) AS hero_component_count",
            "FROM operations_image_skin s",
            "LEFT JOIN (",
            "  SELECT skin_id, COUNT(*) AS asset_count",
            "  FROM operations_image_skin_asset",
            "  WHERE deleted = b'0'",
            "  GROUP BY skin_id",
            ") asset_counts ON asset_counts.skin_id = s.id",
            "LEFT JOIN (",
            "  SELECT skin_id, COUNT(*) AS hero_component_count",
            "  FROM operations_image_skin_component",
            "  WHERE deleted = b'0'",
            "    AND template_role = 'HERO_MAIN'",
            "    AND image_url IS NOT NULL",
            "    AND image_url != ''",
            "  GROUP BY skin_id",
            ") component_counts ON component_counts.skin_id = s.id",
            "WHERE s.owner_user_id = #{ownerUserId}",
            "  AND (",
            "    s.store_code = #{storeCode}",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM logical_store_site requested_site",
            "      JOIN logical_store requested_store",
            "        ON requested_store.id = requested_site.logical_store_id",
            "       AND requested_store.owner_user_id = #{ownerUserId}",
            "       AND requested_store.is_deleted = b'0'",
            "      JOIN logical_store_site skin_site",
            "        ON skin_site.logical_store_id = requested_site.logical_store_id",
            "       AND CONVERT(skin_site.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "         = CONVERT(s.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "       AND skin_site.is_deleted = b'0'",
            "      WHERE requested_site.store_code = #{storeCode}",
            "        AND requested_site.is_deleted = b'0'",
            "    )",
            "  )",
            "  AND s.deleted = b'0'",
            "<if test='status != null and status != \"\"'>",
            "  AND s.status = #{status}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (s.skin_name LIKE CONCAT('%', #{keyword}, '%') OR s.remark LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY s.sort_order ASC, s.updated_at DESC, s.id DESC",
            "</script>"
    })
    List<OperationsSkinRecord> selectSkins(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    @ResultMap("operationsSkinRecordMap")
    @Select({
            "SELECT",
            "  s.id,",
            "  s.owner_user_id,",
            "  s.store_code,",
            "  s.skin_name,",
            "  s.status,",
            "  s.cover_image_url,",
            "  s.style_description,",
            "  s.remark,",
            "  s.sort_order,",
            "  s.created_by,",
            "  s.updated_by,",
            "  s.created_at,",
            "  s.updated_at,",
            "  s.deleted,",
            "  COALESCE(asset_counts.asset_count, 0) AS asset_count,",
            "  COALESCE(component_counts.hero_component_count, 0) AS hero_component_count",
            "FROM operations_image_skin s",
            "LEFT JOIN (",
            "  SELECT skin_id, COUNT(*) AS asset_count",
            "  FROM operations_image_skin_asset",
            "  WHERE deleted = b'0'",
            "  GROUP BY skin_id",
            ") asset_counts ON asset_counts.skin_id = s.id",
            "LEFT JOIN (",
            "  SELECT skin_id, COUNT(*) AS hero_component_count",
            "  FROM operations_image_skin_component",
            "  WHERE deleted = b'0'",
            "    AND template_role = 'HERO_MAIN'",
            "    AND image_url IS NOT NULL",
            "    AND image_url != ''",
            "  GROUP BY skin_id",
            ") component_counts ON component_counts.skin_id = s.id",
            "WHERE s.id = #{id}",
            "  AND s.owner_user_id = #{ownerUserId}",
            "  AND (",
            "    s.store_code = #{storeCode}",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM logical_store_site requested_site",
            "      JOIN logical_store requested_store",
            "        ON requested_store.id = requested_site.logical_store_id",
            "       AND requested_store.owner_user_id = #{ownerUserId}",
            "       AND requested_store.is_deleted = b'0'",
            "      JOIN logical_store_site skin_site",
            "        ON skin_site.logical_store_id = requested_site.logical_store_id",
            "       AND CONVERT(skin_site.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "         = CONVERT(s.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "       AND skin_site.is_deleted = b'0'",
            "      WHERE requested_site.store_code = #{storeCode}",
            "        AND requested_site.is_deleted = b'0'",
            "    )",
            "  )",
            "  AND s.deleted = b'0'"
    })
    OperationsSkinRecord selectSkinById(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM operations_image_skin s",
            "WHERE s.owner_user_id = #{ownerUserId}",
            "  AND (",
            "    s.store_code = #{storeCode}",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM logical_store_site requested_site",
            "      JOIN logical_store requested_store",
            "        ON requested_store.id = requested_site.logical_store_id",
            "       AND requested_store.owner_user_id = #{ownerUserId}",
            "       AND requested_store.is_deleted = b'0'",
            "      JOIN logical_store_site skin_site",
            "        ON skin_site.logical_store_id = requested_site.logical_store_id",
            "       AND CONVERT(skin_site.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "         = CONVERT(s.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "       AND skin_site.is_deleted = b'0'",
            "      WHERE requested_site.store_code = #{storeCode}",
            "        AND requested_site.is_deleted = b'0'",
            "    )",
            "  )",
            "  AND s.skin_name = #{skinName}",
            "  AND s.deleted = b'0'",
            "<if test='excludeId != null'>",
            "  AND s.id != #{excludeId}",
            "</if>",
            "</script>"
    })
    int countByName(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skinName") String skinName,
            @Param("excludeId") Long excludeId
    );

    @Insert({
            "INSERT INTO operations_image_skin_asset (",
            "  skin_id, asset_type, image_url, caption, sort_order, deleted",
            ") VALUES (",
            "  #{skinId}, #{assetType}, #{imageUrl}, #{caption}, #{sortOrder}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    Long insertAsset(OperationsSkinAssetRecord record);

    @Update({
            "UPDATE operations_image_skin_asset",
            "SET deleted = b'1'",
            "WHERE skin_id = #{skinId}",
            "  AND deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_image_skin s",
            "    WHERE s.id = operations_image_skin_asset.skin_id",
            "      AND s.owner_user_id = #{ownerUserId}",
            "      AND s.store_code = #{storeCode}",
            "      AND s.deleted = b'0'",
            "  )"
    })
    int softDeleteAssets(
            @Param("skinId") Long skinId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Results(id = "operationsSkinAssetRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "skin_id", property = "skinId"),
            @Result(column = "asset_type", property = "assetType"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "caption", property = "caption"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT",
            "  a.id,",
            "  a.skin_id,",
            "  a.asset_type,",
            "  a.image_url,",
            "  a.caption,",
            "  a.sort_order,",
            "  a.deleted",
            "FROM operations_image_skin_asset a",
            "JOIN operations_image_skin s ON s.id = a.skin_id",
            "  AND s.owner_user_id = #{ownerUserId}",
            "  AND s.store_code = #{storeCode}",
            "  AND s.deleted = b'0'",
            "WHERE a.skin_id = #{skinId}",
            "  AND a.deleted = b'0'",
            "ORDER BY a.sort_order ASC, a.id ASC"
    })
    List<OperationsSkinAssetRecord> selectAssets(
            @Param("skinId") Long skinId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Insert({
            "INSERT INTO operations_image_skin_component (",
            "  skin_id, template_role, component_key, image_url, x, y, width, height, z_index,",
            "  required, locked, style_json, created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{skinId}, #{templateRole}, #{componentKey}, #{imageUrl}, #{x}, #{y}, #{width}, #{height}, #{zIndex},",
            "  #{required}, #{locked}, #{styleJson}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    Long insertComponent(OperationsSkinComponentRecord record);

    @Update({
            "UPDATE operations_image_skin_component",
            "SET deleted = b'1',",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE skin_id = #{skinId}",
            "  AND deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_image_skin s",
            "    WHERE s.id = operations_image_skin_component.skin_id",
            "      AND s.owner_user_id = #{ownerUserId}",
            "      AND s.store_code = #{storeCode}",
            "      AND s.deleted = b'0'",
            "  )"
    })
    int softDeleteComponents(
            @Param("skinId") Long skinId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("updatedBy") Long updatedBy
    );

    @Results(id = "operationsSkinComponentRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "skin_id", property = "skinId"),
            @Result(column = "template_role", property = "templateRole"),
            @Result(column = "component_key", property = "componentKey"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "x", property = "x"),
            @Result(column = "y", property = "y"),
            @Result(column = "width", property = "width"),
            @Result(column = "height", property = "height"),
            @Result(column = "z_index", property = "zIndex"),
            @Result(column = "required", property = "required", javaType = Boolean.class),
            @Result(column = "locked", property = "locked", javaType = Boolean.class),
            @Result(column = "style_json", property = "styleJson"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT",
            "  c.id,",
            "  c.skin_id,",
            "  c.template_role,",
            "  c.component_key,",
            "  c.image_url,",
            "  c.x,",
            "  c.y,",
            "  c.width,",
            "  c.height,",
            "  c.z_index,",
            "  c.required,",
            "  c.locked,",
            "  c.style_json,",
            "  c.created_by,",
            "  c.updated_by,",
            "  c.created_at,",
            "  c.updated_at,",
            "  c.deleted",
            "FROM operations_image_skin_component c",
            "JOIN operations_image_skin s ON s.id = c.skin_id",
            "  AND s.owner_user_id = #{ownerUserId}",
            "  AND s.store_code = #{storeCode}",
            "  AND s.deleted = b'0'",
            "WHERE c.skin_id = #{skinId}",
            "  AND c.deleted = b'0'",
            "ORDER BY c.template_role ASC, c.z_index ASC, c.id ASC"
    })
    List<OperationsSkinComponentRecord> selectComponents(
            @Param("skinId") Long skinId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );
}
