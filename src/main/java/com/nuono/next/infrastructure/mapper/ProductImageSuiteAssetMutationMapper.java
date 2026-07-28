package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Mutation boundary for image-suite assets.
 *
 * <p>Every write is fenced against an in-flight suite publication so a publish manifest cannot
 * race with asset deletion, movement, or reordering.</p>
 */
public interface ProductImageSuiteAssetMutationMapper {

    @Delete({
            "DELETE asset",
            "FROM product_image_suite_asset asset",
            "JOIN product_image_suite suite ON suite.id = asset.suite_id",
            "WHERE asset.id = #{assetId}",
            "  AND asset.suite_id = #{suiteId}",
            "  AND suite.suite_status <> 'PUBLISHING'",
            "  AND suite.deleted = b'0'"
    })
    int deleteSuiteAsset(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId
    );

    @Update({
            "UPDATE product_image_suite_asset asset",
            "JOIN product_image_suite source_suite ON source_suite.id = asset.suite_id",
            "JOIN product_image_suite target_suite ON target_suite.id = #{targetSuiteId}",
            "SET asset.suite_id = #{targetSuiteId},",
            "    asset.sort_order = #{sortOrder}",
            "WHERE asset.id = #{assetId}",
            "  AND asset.suite_id = #{sourceSuiteId}",
            "  AND source_suite.suite_status <> 'PUBLISHING'",
            "  AND target_suite.suite_status <> 'PUBLISHING'",
            "  AND source_suite.deleted = b'0'",
            "  AND target_suite.deleted = b'0'"
    })
    int moveSuiteAssetToSuite(
            @Param("sourceSuiteId") Long sourceSuiteId,
            @Param("assetId") Long assetId,
            @Param("targetSuiteId") Long targetSuiteId,
            @Param("sortOrder") Integer sortOrder
    );

    @Update({
            "UPDATE product_image_suite_asset asset",
            "JOIN product_image_suite suite ON suite.id = asset.suite_id",
            "SET asset.sort_order = #{sortOrder}",
            "WHERE asset.id = #{assetId}",
            "  AND asset.suite_id = #{suiteId}",
            "  AND suite.suite_status <> 'PUBLISHING'",
            "  AND suite.deleted = b'0'"
    })
    int updateSuiteAssetSortOrder(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId,
            @Param("sortOrder") Integer sortOrder
    );

    @Select({
            "SELECT COALESCE(MAX(sort_order), 0)",
            "FROM product_image_suite_asset",
            "WHERE suite_id = #{suiteId}"
    })
    int selectMaxSuiteAssetSortOrder(@Param("suiteId") Long suiteId);
}
