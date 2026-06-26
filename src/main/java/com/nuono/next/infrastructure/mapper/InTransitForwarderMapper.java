package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitForwarderMapper extends InTransitGoodsSequenceMapper {

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND id = #{forwarderId} AND is_deleted = b'0' LIMIT 1"})
    ForwarderRow selectForwarderById(@Param("ownerUserId") Long ownerUserId, @Param("forwarderId") Long forwarderId);

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND forwarder_code = #{forwarderCode} AND is_deleted = b'0' LIMIT 1"})
    ForwarderRow selectForwarderByOwnerAndCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("forwarderCode") String forwarderCode
    );

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND is_deleted = b'0' ORDER BY forwarder_name ASC, id ASC"})
    List<ForwarderRow> listForwarders(@Param("ownerUserId") Long ownerUserId);

    @Insert({
            "INSERT INTO in_transit_forwarder (",
            "id, owner_user_id, forwarder_code, forwarder_name, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.forwarderCode}, #{row.forwarderName}, #{row.status}, b'0',",
            "#{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertForwarder(@Param("row") ForwarderRow row);

    @Update({
            "UPDATE in_transit_forwarder",
            "SET forwarder_name = #{row.forwarderName}, status = #{row.status}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateForwarder(@Param("row") ForwarderRow row);

    @Select({ALIAS_SELECT, "WHERE alias.owner_user_id = #{ownerUserId} AND alias.id = #{aliasId} AND alias.is_deleted = b'0' LIMIT 1"})
    ForwarderAliasRow selectAliasById(@Param("ownerUserId") Long ownerUserId, @Param("aliasId") Long aliasId);

    @Select({
            ALIAS_SELECT,
            "WHERE alias.owner_user_id = #{ownerUserId}",
            "AND alias.normalized_raw_forwarder_name = #{normalizedRawForwarderName}",
            "AND alias.is_deleted = b'0' LIMIT 1"
    })
    ForwarderAliasRow selectAliasByOwnerAndNormalized(
            @Param("ownerUserId") Long ownerUserId,
            @Param("normalizedRawForwarderName") String normalizedRawForwarderName
    );

    @Select({
            ALIAS_SELECT,
            "WHERE alias.owner_user_id = #{ownerUserId}",
            "AND alias.normalized_raw_forwarder_name = #{normalizedRawForwarderName}",
            "AND alias.status = 'ACTIVE'",
            "AND alias.is_deleted = b'0' LIMIT 1"
    })
    ForwarderAliasRow selectActiveAliasByOwnerAndNormalized(
            @Param("ownerUserId") Long ownerUserId,
            @Param("normalizedRawForwarderName") String normalizedRawForwarderName
    );

    @Insert({
            "INSERT INTO in_transit_forwarder_alias (",
            "id, owner_user_id, standard_forwarder_id, raw_forwarder_name, normalized_raw_forwarder_name,",
            "status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.standardForwarderId}, #{row.rawForwarderName},",
            "#{row.normalizedRawForwarderName}, #{row.status}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertForwarderAlias(@Param("row") ForwarderAliasRow row);

    @Update({
            "UPDATE in_transit_forwarder_alias",
            "SET standard_forwarder_id = #{row.standardForwarderId}, raw_forwarder_name = #{row.rawForwarderName},",
            "status = #{row.status}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateForwarderAlias(@Param("row") ForwarderAliasRow row);
}
