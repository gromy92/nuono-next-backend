package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.advertising.AdvertisingIdBlockCommand;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.SelectKey;

/** Reserves a whole generation ID range with one sequence-row mutation. */
public interface Dp06AdvertisingIdMapper {
    @Insert({
            "INSERT INTO noon_ad_id_sequence (sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES (#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE",
            " next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedEnd",
            before = false,
            resultType = Long.class
    )
    void reserve(AdvertisingIdBlockCommand command);
}
